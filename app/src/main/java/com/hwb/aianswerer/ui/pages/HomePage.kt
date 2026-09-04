package com.hwb.aianswerer.ui.pages

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hwb.aianswerer.config.AppConfig
import com.hwb.aianswerer.ui.components.TitleSection
import com.hwb.aianswerer.ui.theme.*

@Preview(showSystemUi = true, showBackground = true)
@Composable
private fun HomePreview() = Themed {
    HomePage(
        t = it,
        onSettingsClick = {},
        onApiConfigClick = {},
        onSystemPromptClick = {},
        onConversationsClick = {},
        onAccessibilityPermissionClick = {},
        onOverlayPermissionClick = {},
        onNotificationPermissionClick = {},
        onStartClick = {}
    )
}

@Composable
fun HomePage(
    t: Th,
    onSettingsClick: () -> Unit,
    onApiConfigClick: () -> Unit,
    onSystemPromptClick: () -> Unit,
    onConversationsClick: () -> Unit,
    onAccessibilityPermissionClick: () -> Unit,
    onOverlayPermissionClick: () -> Unit,
    onNotificationPermissionClick: () -> Unit,
    onStartClick: () -> Unit,
    captureMode: String = AppConfig.CAPTURE_MODE_HYBRID,
    onCaptureModeChange: (String) -> Unit = {},
    screenCaptureUserChoice: Boolean = false,
    onScreenCaptureUserChoiceChange: (Boolean) -> Unit = {},
    hasAccessibilityPermission: Boolean = true,
    hasOverlayPermission: Boolean = true,
    hasNotificationPermission: Boolean = true,
    isAnswerModeActive: Boolean = false,
    onStopClick: () -> Unit = {}
) {
    val background = t.bg1

    Box(Modifier.fillMaxSize().background(background)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(top = 116.dp, start = 20.dp, end = 20.dp, bottom = 116.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            RecognitionModeSelector(t, captureMode, onCaptureModeChange)
            if (captureMode != AppConfig.CAPTURE_MODE_ACCESSIBILITY &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
            ) {
                ScreenCaptureScopeSetting(
                    t = t,
                    allowUserChoice = screenCaptureUserChoice,
                    onChanged = onScreenCaptureUserChoiceChange
                )
            }

            if (!hasOverlayPermission) {
                PermissionHint(t, "需要悬浮窗权限，点击前往开启", onOverlayPermissionClick)
            }
            if (!hasAccessibilityPermission) {
                PermissionHint(
                    t,
                    if (captureMode == AppConfig.CAPTURE_MODE_SCREENSHOT) {
                        "开启无障碍截图可减少系统共享确认，点击开启"
                    } else {
                        "需要屏幕读取权限，点击前往开启"
                    },
                    onAccessibilityPermissionClick
                )
            }
            if (!hasNotificationPermission) {
                PermissionHint(t, "需要通知权限以保持助手运行，点击开启", onNotificationPermissionClick)
            }

            Text("聊天配置", style = DW.LabelSmall.copy(color = t.osv), modifier = Modifier.padding(start = 4.dp, top = 8.dp))
            HomeEntry(t, "API 配置", "模型、地址与密钥", t.p, onApiConfigClick)
            HomeEntry(t, "系统提示词", "设置默认回复方式", t.ac, onSystemPromptClick)
            HomeEntry(t, "会话管理", "联系人、历史与个性化配置", t.p, onConversationsClick)
        }

        Box(Modifier.fillMaxWidth().background(t.bg1)) {
            TitleSection(t, onSettingsClick)
        }
        AssistantSwitchBar(
            t = t,
            enabled = isAnswerModeActive,
            onToggle = { enabled -> if (enabled) onStartClick() else onStopClick() },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun ScreenCaptureScopeSetting(
    t: Th,
    allowUserChoice: Boolean,
    onChanged: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(t.gt)
            .border(1.dp, t.gb, RoundedCornerShape(18.dp))
            .clickable { onChanged(!allowUserChoice) }
            .padding(horizontal = 17.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(com.hwb.aianswerer.R.string.screen_capture_scope_title),
                style = DW.TitleMedium.copy(color = t.ob)
            )
            Text(
                stringResource(
                    if (allowUserChoice) {
                        com.hwb.aianswerer.R.string.screen_capture_scope_user_choice_desc
                    } else {
                        com.hwb.aianswerer.R.string.screen_capture_scope_full_display_desc
                    }
                ),
                style = DW.BodySmall.copy(color = t.osv)
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = allowUserChoice,
            onCheckedChange = onChanged,
            colors = SwitchDefaults.colors(checkedTrackColor = t.p, checkedThumbColor = Color.White)
        )
    }
}

@Composable
private fun RecognitionModeSelector(t: Th, selected: String, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val modes = listOf(
        AppConfig.CAPTURE_MODE_HYBRID to "混合识别",
        AppConfig.CAPTURE_MODE_ACCESSIBILITY to "仅屏幕读取",
        AppConfig.CAPTURE_MODE_SCREENSHOT to "仅截图识别"
    )
    val title = modes.firstOrNull { it.first == selected }?.second ?: "混合识别"
    Box {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(t.gt)
                .border(1.dp, t.gb, RoundedCornerShape(22.dp)).clickable { expanded = true }
                .padding(horizontal = 18.dp, vertical = 17.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("识别方式", style = DW.LabelSmall.copy(color = t.p))
                Spacer(Modifier.height(4.dp))
                Text(title, style = DW.TitleMedium.copy(color = t.ob, fontWeight = FontWeight.SemiBold))
                Text(
                    when (selected) {
                        AppConfig.CAPTURE_MODE_ACCESSIBILITY -> "只读取当前屏幕文字"
                        AppConfig.CAPTURE_MODE_SCREENSHOT -> "每次使用截图识别"
                        else -> "优先屏幕读取，失败后自动截图"
                    },
                    style = DW.BodySmall.copy(color = t.osv)
                )
            }
            Text("⌄", style = DW.TitleLarge.copy(color = t.p))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            modes.forEach { (mode, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = { onSelected(mode); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun PermissionHint(t: Th, text: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(t.ac.copy(alpha = if (t.isLight) .10f else .18f))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("!", color = t.ac, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(9.dp))
        Text(text, style = DW.BodySmall.copy(color = t.ob), modifier = Modifier.weight(1f))
        Text("›", color = t.ac)
    }
}

@Composable
private fun HomeEntry(t: Th, title: String, subtitle: String, accent: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(t.gt)
            .border(1.dp, accent.copy(alpha = .18f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick).padding(17.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(9.dp).background(accent, RoundedCornerShape(5.dp)))
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = DW.TitleMedium.copy(color = t.ob))
            Text(subtitle, style = DW.BodySmall.copy(color = t.osv))
        }
        Text("›", style = DW.TitleLarge.copy(color = t.osv))
    }
}

@Composable
private fun AssistantSwitchBar(t: Th, enabled: Boolean, onToggle: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().background(t.bg1).padding(horizontal = 20.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(24.dp)).background(t.gt).border(1.dp, t.gb, RoundedCornerShape(24.dp))
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("聊天助手", style = DW.TitleMedium.copy(color = t.ob, fontWeight = FontWeight.SemiBold))
            Text(if (enabled) "运行中" else "已关闭", style = DW.BodySmall.copy(color = if (enabled) t.ok else t.osv))
        }
        Switch(
            checked = enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(checkedTrackColor = t.p, checkedThumbColor = Color.White)
        )
    }
}
