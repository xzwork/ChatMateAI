package com.hwb.aianswerer.chat.capture

import android.content.Context
import com.hwb.aianswerer.ScreenCaptureManager
import com.hwb.aianswerer.ScreenReaderService
import com.hwb.aianswerer.chat.ChatSession
import com.hwb.aianswerer.chat.model.ScreenNode
import com.hwb.aianswerer.config.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ScreenSnapshot(
    val packageName: String,
    val appName: String,
    val nodes: List<ScreenNode>,
    val screenWidth: Int,
    val screenHeight: Int,
    val usedOcrFallback: Boolean
)

class ScreenshotPermissionRequiredException : IllegalStateException(
    "无障碍截图不可用，需要确认一次系统屏幕共享授权"
)

class ScreenCaptureEngine(
    private val context: Context,
    private val screenCaptureManager: ScreenCaptureManager?,
    private val ocrReader: OcrReader = OcrReader()
) {
    suspend fun capture(
        forceScreenshot: Boolean = false,
        captureMode: String = AppConfig.getCaptureMode()
    ): ScreenSnapshot {
        val metrics = context.resources.displayMetrics
        val accessibilityNodes = ScreenReaderService.readScreenNodes()
        val accessibilityPackage = accessibilityNodes.groupingBy { it.packageName }
            .eachCount().filterKeys(::isUsablePackage).maxByOrNull { it.value }?.key
        val hasReadableScreen = accessibilityNodes.count { it.text.isNotBlank() } >= 3 && accessibilityPackage != null
        val effectiveMode = if (forceScreenshot) AppConfig.CAPTURE_MODE_SCREENSHOT else captureMode

        if (effectiveMode != AppConfig.CAPTURE_MODE_SCREENSHOT && hasReadableScreen) {
            return snapshot(accessibilityPackage!!, accessibilityNodes, metrics.widthPixels, metrics.heightPixels, false)
        }
        if (effectiveMode == AppConfig.CAPTURE_MODE_ACCESSIBILITY) {
            throw IllegalStateException("屏幕读取失败，请检查无障碍权限或改用混合识别")
        }

        val packageName = accessibilityPackage
            ?: ScreenReaderService.currentWindowPackage()
            ?: currentForegroundPackage(accessibilityNodes)
            ?: ScreenReaderService.currentForegroundPackage()?.takeIf(::isUsablePackage)
            ?: ChatSession.active?.screen?.conversation?.packageName?.takeIf(::isUsablePackage)
            ?: "screen.capture"
        val accessibilityBitmap = runCatching { ScreenReaderService.captureScreenshot() }.getOrNull()
        val bitmap = accessibilityBitmap
            ?: screenCaptureManager?.takeIf { it.isReady }?.captureScreen()
            ?: throw ScreenshotPermissionRequiredException()
        val nodes = try { ocrReader.read(bitmap, packageName) } finally { if (!bitmap.isRecycled) bitmap.recycle() }
        if (nodes.isEmpty()) throw IllegalStateException("无法识别当前聊天内容")
        return snapshot(packageName, nodes, metrics.widthPixels, metrics.heightPixels, true)
    }

    private fun currentForegroundPackage(nodes: List<ScreenNode>): String? =
        nodes.firstOrNull { isUsablePackage(it.packageName) }?.packageName

    private fun isUsablePackage(value: String): Boolean =
        value.isNotBlank() && value != context.packageName && value != "android" &&
            value != "com.android.systemui" && value != "unknown.app" && value != "screen.capture"

    private suspend fun snapshot(packageName: String, nodes: List<ScreenNode>, width: Int, height: Int, ocr: Boolean): ScreenSnapshot {
        val appName = withContext(Dispatchers.IO) {
            try {
                val info = context.packageManager.getApplicationInfo(packageName, 0)
                context.packageManager.getApplicationLabel(info).toString()
            } catch (_: Exception) { if (packageName == "screen.capture") "截图识别" else packageName }
        }
        return ScreenSnapshot(packageName, appName, nodes.filter { it.packageName.isBlank() || it.packageName == packageName }, width, height, ocr)
    }
}
