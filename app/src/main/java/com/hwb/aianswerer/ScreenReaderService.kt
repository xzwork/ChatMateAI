package com.hwb.aianswerer

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityEvent
import com.hwb.aianswerer.utils.AppLog
import com.hwb.aianswerer.chat.model.NodeSource
import com.hwb.aianswerer.chat.model.ScreenNode
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 无障碍屏幕读取服务 — 通过 AccessibilityService 读取屏幕文本内容。
 *
 * 替代截图 + OCR 的方式，直接获取屏幕上的文字节点，速度更快、无需截图权限。
 *
 * 使用方式：
 *   1. 用户在系统设置中启用此服务
 *   2. FloatingWindowService 在"屏幕读取"模式下调用 ScreenReaderService.readScreenText()
 *   3. 返回拼接后的文本，交给 AI 分析
 */
class ScreenReaderService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
        AppLog.d("ScreenReaderService 已连接")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 仅记录最近前台包名；不在事件回调中读取或保存屏幕内容。
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        event.packageName?.toString()?.takeIf { isUsableTargetPackage(it, packageName) }?.let {
            lastForegroundPackage = it
        }
    }

    override fun onInterrupt() {
        AppLog.d("ScreenReaderService 被中断")
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
        AppLog.d("ScreenReaderService 已销毁")
    }

    companion object {
        var instance: ScreenReaderService? = null
            private set

        @Volatile private var lastForegroundPackage: String? = null
        fun currentForegroundPackage(): String? = lastForegroundPackage

        /** Reads the active app directly from accessibility windows before using the cached event package. */
        fun currentWindowPackage(): String? {
            val service = instance ?: return currentForegroundPackage()
            @Suppress("DEPRECATION")
            val appWindows = service.windows.orEmpty().filter {
                it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION
            }
            val activeWindow = appWindows.firstOrNull { it.isActive } ?: appWindows.firstOrNull()
            val root = try { activeWindow?.root ?: service.rootInActiveWindow } catch (_: Exception) { null }
            val resolved = try {
                root?.packageName?.toString()?.takeIf { isUsableTargetPackage(it, service.packageName) }
            } finally {
                root?.recycle()
            }
            return resolved ?: currentForegroundPackage()?.takeIf { isUsableTargetPackage(it, service.packageName) }
        }

        private fun isUsableTargetPackage(value: String, ownPackage: String): Boolean {
            if (value.isBlank() || value == ownPackage || value in setOf("android", "com.android.systemui", "unknown.app")) return false
            val inputManager = instance?.getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
            return inputManager?.enabledInputMethodList.orEmpty().none { it.packageName == value }
        }

        /** 服务是否已连接并可用 */
        val isActive: Boolean get() = instance != null

        /**
         * 检查无障碍服务是否在系统设置中已启用（不要求服务当前运行）
         */
        fun isAccessibilityServiceEnabled(context: Context): Boolean {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
                ?: return false
            val services = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                @Suppress("DEPRECATION")
                am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            } else {
                @Suppress("DEPRECATION")
                am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            }
            return services.any { info ->
                val si = info.resolveInfo.serviceInfo
                si.packageName == context.packageName
                    && si.name.contains("ScreenReaderService")
            }
        }

        /**
         * 读取当前屏幕上的所有文本内容。
         * @return 拼接后的屏幕文本，如果服务不可用返回 null
         */
        fun readScreenText(): String? {
            val service = instance ?: return null

            // Iterate all windows to find text — floating overlay blocks rootInActiveWindow
            @Suppress("DEPRECATION")
            val allWindows = service.windows ?: emptyList()
            for (window in allWindows) {
                if (window == null) continue
                try {
                    @Suppress("DEPRECATION")
                    val root = window.getRoot() ?: continue
                    // Skip our own empty floating window
                    if (root.packageName == service.packageName && root.childCount <= 1) {
                        root.recycle()
                        continue
                    }
                    val textBuilder = StringBuilder()
                    collectText(root, textBuilder)
                    root.recycle()
                    val text = textBuilder.toString().trim()
                    if (text.isNotEmpty()) return text
                } catch (_: Exception) {
                    // skip inaccessible windows
                }
            }

            // Fallback: try rootInActiveWindow
            val rootNode = service.rootInActiveWindow ?: return null
            try {
                val textBuilder = StringBuilder()
                collectText(rootNode, textBuilder)
                val text = textBuilder.toString().trim()
                return text.ifEmpty { null }
            } finally {
                rootNode.recycle()
            }
        }

        /** Takes a one-shot accessibility snapshot with geometry for chat parsing. */
        fun readScreenNodes(): List<ScreenNode> {
            val service = instance ?: return emptyList()
            val candidates = mutableListOf<Pair<Boolean, List<ScreenNode>>>()
            @Suppress("DEPRECATION")
            for (window in service.windows.orEmpty()) {
                if (window.type != android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION) continue
                val root = try { window.root } catch (_: Exception) { null } ?: continue
                try {
                    val rootPackage = root.packageName?.toString().orEmpty()
                    if (!isUsableTargetPackage(rootPackage, service.packageName)) continue
                    val snapshot = mutableListOf<ScreenNode>()
                    collectNodes(root, snapshot)
                    candidates.add((window.isActive) to snapshot)
                } finally {
                    root.recycle()
                }
            }
            if (candidates.isNotEmpty()) {
                return candidates.maxWithOrNull(compareBy<Pair<Boolean, List<ScreenNode>>> { it.first }.thenBy { it.second.size })
                    ?.second.orEmpty()
            }
            val root = service.rootInActiveWindow ?: return emptyList()
            return try {
                mutableListOf<ScreenNode>().also { collectNodes(root, it) }
            } finally { root.recycle() }
        }

        /**
         * Captures the default display through the enabled accessibility service.
         * Android does not show a MediaProjection confirmation for this API.
         */
        suspend fun captureScreenshot(): Bitmap {
            val service = instance ?: throw IllegalStateException("无障碍服务未连接")
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                throw IllegalStateException("当前 Android 版本不支持无障碍截图")
            }
            return suspendCancellableCoroutine { continuation ->
                service.takeScreenshot(
                    android.view.Display.DEFAULT_DISPLAY,
                    service.mainExecutor,
                    object : TakeScreenshotCallback {
                        override fun onSuccess(result: ScreenshotResult) {
                            val buffer = result.hardwareBuffer
                            try {
                                val hardwareBitmap = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                                val bitmap = hardwareBitmap?.copy(Bitmap.Config.ARGB_8888, false)
                                hardwareBitmap?.recycle()
                                if (bitmap != null && continuation.isActive) continuation.resume(bitmap)
                                else if (continuation.isActive) {
                                    continuation.resumeWithException(IllegalStateException("无障碍截图转换失败"))
                                } else bitmap?.recycle()
                            } catch (error: Exception) {
                                if (continuation.isActive) continuation.resumeWithException(error)
                            } finally {
                                buffer.close()
                            }
                        }

                        override fun onFailure(errorCode: Int) {
                            if (continuation.isActive) {
                                continuation.resumeWithException(
                                    IllegalStateException("无障碍截图失败（错误码 $errorCode）")
                                )
                            }
                        }
                    }
                )
            }
        }

        private fun collectNodes(node: AccessibilityNodeInfo, output: MutableList<ScreenNode>) {
            if (!node.isVisibleToUser || node.isPassword) return
            val text = node.text?.toString()?.trim().orEmpty()
            if (text.isNotEmpty() || node.isEditable || !node.contentDescription.isNullOrBlank()) {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                if (!bounds.isEmpty) {
                    output += ScreenNode(
                        text = text,
                        bounds = bounds,
                        packageName = node.packageName?.toString().orEmpty(),
                        className = node.className?.toString(),
                        viewId = node.viewIdResourceName,
                        contentDescription = node.contentDescription?.toString(),
                        source = NodeSource.ACCESSIBILITY,
                        isEditable = node.isEditable,
                        isClickable = node.isClickable
                    )
                }
            }
            for (index in 0 until node.childCount) {
                val child = node.getChild(index) ?: continue
                try { collectNodes(child, output) } finally { child.recycle() }
            }
        }

        /**
         * 递归遍历节点树，收集所有可见文本。
         * 跳过不可见、无文本的节点，避免重复内容。
         */
        private fun collectText(node: AccessibilityNodeInfo, builder: StringBuilder) {
            // 跳过不可见的节点
            if (!node.isVisibleToUser) return

            // 收集节点文本
            val nodeText = node.text?.toString()
            if (!nodeText.isNullOrBlank()) {
                // 简单去重：避免连续重复行
                val lastLine = builder.lines().lastOrNull { it.isNotBlank() }
                if (lastLine != nodeText.trim()) {
                    builder.appendLine(nodeText.trim())
                }
            }

            // 收集 contentDescription（图标按钮等无 text 但有描述的元素）
            val desc = node.contentDescription?.toString()
            if (!desc.isNullOrBlank() && desc != nodeText) {
                // 不添加 contentDescription，避免噪音（按钮描述等）
            }

            // 递归子节点
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                try {
                    collectText(child, builder)
                } finally {
                    // 确保子节点被回收
                    child.recycle()
                }
            }
        }
    }
}
