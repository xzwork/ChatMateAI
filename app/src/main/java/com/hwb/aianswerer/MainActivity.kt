package com.hwb.aianswerer

import android.Manifest
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.hwb.aianswerer.config.AppConfig
import com.hwb.aianswerer.utils.AppLog
import com.hwb.aianswerer.ui.pages.HomePage
import com.hwb.aianswerer.ui.theme.sandboxTheme
import com.hwb.aianswerer.ui.theme.*

class MainActivity : BaseActivity() {

    companion object {
        const val EXTRA_START_FROM_QUICK_TILE = "start_from_quick_tile"
        const val EXTRA_REQUEST_SCREEN_CAPTURE = "request_screen_capture"
    }

    private var isAnswerModeActive by mutableStateOf(false)
    private var screenCaptureResultCode: Int? = null
    private var screenCaptureData: Intent? = null
    private var selectedQuestionTypes by mutableStateOf<Set<String>>(emptySet())
    private var cropMode by mutableStateOf(AppConfig.CROP_MODE_FULL)
    private var captureMode by mutableStateOf(AppConfig.CAPTURE_MODE_HYBRID)
    private var screenCaptureUserChoice by mutableStateOf(false)
    private var hasAccessibilityPermission by mutableStateOf(false)
    private var hasOverlayPermission by mutableStateOf(false)
    private var hasNotificationPermission by mutableStateOf(true)
    private var startAfterNotificationPermission = false
    private var isRequestingCapturePermission = false

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isRequestingCapturePermission = false
        if (result.resultCode == RESULT_OK) {
            screenCaptureResultCode = result.resultCode
            screenCaptureData = result.data
            if (checkOverlayPermission()) startAnswerMode()
            else requestOverlayPermission()
        } else {
            Toast.makeText(this, getString(R.string.toast_permission_capture_required), Toast.LENGTH_LONG).show()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        refreshPermissionState()
        if (startAfterNotificationPermission) proceedWithStartFlow()
        startAfterNotificationPermission = false
    }

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (checkOverlayPermission()) {
            continueAfterOverlayPermission()
        } else {
            Toast.makeText(this, getString(R.string.toast_permission_overlay_required), Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        selectedQuestionTypes = AppConfig.getQuestionTypes()
        cropMode = AppConfig.getCropMode()
        captureMode = AppConfig.getCaptureMode()
        screenCaptureUserChoice = AppConfig.getScreenCaptureUserChoice()
        refreshPermissionState()
        setContent {
            val t = sandboxTheme()
            HomePage(
                t = t,
                onSettingsClick = { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) },
                onApiConfigClick = { startActivity(Intent(this@MainActivity, ModelSettingsActivity::class.java)) },
                onSystemPromptClick = { startActivity(Intent(this@MainActivity, com.hwb.aianswerer.chat.ui.SystemPromptSettingsActivity::class.java)) },
                onConversationsClick = { startActivity(Intent(this@MainActivity, com.hwb.aianswerer.chat.ui.ChatSettingsActivity::class.java)) },
                onAccessibilityPermissionClick = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                onOverlayPermissionClick = { requestOverlayPermission() },
                onNotificationPermissionClick = { requestNotificationPermission() },
                onStartClick = { checkAndRequestPermissions() },
                captureMode = captureMode,
                onCaptureModeChange = {
                    captureMode = it
                    AppConfig.saveCaptureMode(it)
                },
                screenCaptureUserChoice = screenCaptureUserChoice,
                onScreenCaptureUserChoiceChange = {
                    screenCaptureUserChoice = it
                    AppConfig.saveScreenCaptureUserChoice(it)
                },
                hasAccessibilityPermission = hasAccessibilityPermission,
                hasOverlayPermission = hasOverlayPermission,
                hasNotificationPermission = hasNotificationPermission,
                isAnswerModeActive = isAnswerModeActive,
                onStopClick = { stopAnswerMode() }
            )
        }
        handleExternalIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleExternalIntent(intent)
    }

    override fun onPostResume() {
        super.onPostResume()
        window.decorView.post { requestQuickTileOnce() }
    }

    private fun requestQuickTileOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (isRequestingCapturePermission) return
        val preferences = getSharedPreferences("quick_settings_tile", Context.MODE_PRIVATE)
        if (preferences.getBoolean("native_add_requested", false)) return

        val statusBarManager = getSystemService(StatusBarManager::class.java) ?: return
        preferences.edit().putBoolean("native_add_requested", true).apply()
        runCatching {
            statusBarManager.requestAddTileService(
                ComponentName(this, ChatAssistantTileService::class.java),
                getString(R.string.quick_tile_label),
                Icon.createWithResource(this, R.drawable.ic_notification),
                mainExecutor
            ) { result ->
                AppLog.d("MainActivity", "Quick Settings tile request result: $result")
            }
        }.onFailure { error ->
            preferences.edit().remove("native_add_requested").apply()
            AppLog.w("MainActivity", "Unable to request Quick Settings tile", error)
        }
    }

    private fun handleExternalIntent(source: Intent?) {
        if (source?.getBooleanExtra(EXTRA_REQUEST_SCREEN_CAPTURE, false) == true) {
            source.removeExtra(EXTRA_REQUEST_SCREEN_CAPTURE)
            window.decorView.post { requestScreenCapturePermission() }
            return
        }
        if (source?.getBooleanExtra(EXTRA_START_FROM_QUICK_TILE, false) == true) {
            source.removeExtra(EXTRA_START_FROM_QUICK_TILE)
            window.decorView.post { if (!FloatingWindowService.isRunning) checkAndRequestPermissions() }
        }
    }

    private fun checkAndRequestPermissions() {
        // Android 13+ requires POST_NOTIFICATIONS as a runtime permission. Request it
        // before starting the foreground service, otherwise the persistent notification
        // is silently suppressed and the service may be killed after a timeout.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            startAfterNotificationPermission = true
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        proceedWithStartFlow()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            startAfterNotificationPermission = false
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun proceedWithStartFlow() {
        if (!checkOverlayPermission()) { requestOverlayPermission(); return }
        continueAfterOverlayPermission()
    }

    private fun continueAfterOverlayPermission() {
        val accessibilityEnabled = ScreenReaderService.isAccessibilityServiceEnabled(this)
        if (accessibilityEnabled) {
            startAnswerMode()
            return
        }
        if (captureMode == AppConfig.CAPTURE_MODE_ACCESSIBILITY) {
            if (!accessibilityEnabled) {
                Toast.makeText(this, "请先开启屏幕读取权限", Toast.LENGTH_SHORT).show()
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } else {
                startAnswerMode()
            }
            return
        }
        if (screenCaptureResultCode != null && screenCaptureData != null) startAnswerMode()
        else requestScreenCapturePermission()
    }

    private fun checkOverlayPermission(): Boolean = Settings.canDrawOverlays(this)

    private fun requestOverlayPermission() {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        overlayPermissionLauncher.launch(intent)
    }

    private fun requestScreenCapturePermission() {
        val screenCaptureManager = ScreenCaptureManager(this)
        isRequestingCapturePermission = true
        screenCaptureLauncher.launch(
            screenCaptureManager.createScreenCaptureIntent(screenCaptureUserChoice)
        )
    }

    private fun startAnswerMode() {
        isRequestingCapturePermission = false
        // Re-read settings in case user changed them on HomePage
        selectedQuestionTypes = AppConfig.getQuestionTypes()
        cropMode = AppConfig.getCropMode()
        val intent = Intent(this, FloatingWindowService::class.java).apply {
            val resultCode = screenCaptureResultCode
            val data = screenCaptureData
            if (resultCode != null && data != null) {
                putExtra("resultCode", resultCode)
                putExtra("data", data)
            }
            putStringArrayListExtra("questionTypes", ArrayList(selectedQuestionTypes))
            putExtra("cropMode", cropMode)
            putExtra("captureMode", captureMode)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
            else startService(intent)
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.toast_mode_start_failed, e.message ?: ""), Toast.LENGTH_LONG).show()
            return
        }
        isAnswerModeActive = true
        Toast.makeText(this, getString(R.string.toast_mode_started), Toast.LENGTH_SHORT).show()
        requestBatteryOptimizationExemption()
        moveTaskToBack(true)
    }

    private fun requestBatteryOptimizationExemption() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                AppLog.w("MainActivity", "Cannot open battery optimization directly", e)
            }
        }
    }

    private fun stopAnswerMode() {
        stopService(Intent(this, FloatingWindowService::class.java))
        isAnswerModeActive = false
        screenCaptureResultCode = null
        screenCaptureData = null
        Toast.makeText(this, getString(R.string.toast_mode_stopped), Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionState()
        if (isAnswerModeActive != FloatingWindowService.isRunning) {
            isAnswerModeActive = FloatingWindowService.isRunning
            if (!FloatingWindowService.isRunning) {
                screenCaptureResultCode = null
                screenCaptureData = null
            }
        }
        // 通知悬浮窗刷新设置
        if (FloatingWindowService.isRunning) {
            sendBroadcast(Intent(Constants.ACTION_REFRESH_SETTINGS).setPackage(packageName))
        }
    }

    private fun refreshPermissionState() {
        hasOverlayPermission = checkOverlayPermission()
        hasAccessibilityPermission = ScreenReaderService.isAccessibilityServiceEnabled(this)
        hasNotificationPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}
