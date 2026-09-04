package com.hwb.aianswerer.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hwb.aianswerer.ui.theme.*

@Preview(showSystemUi = true, showBackground = true)
@Composable private fun SettingsPreview() = Themed { SettingsPage(it, {}, {}, {}) }

@Composable
fun SettingsPage(
    t: Th,
    onBack: () -> Unit,
    onWebSearch: () -> Unit = {},
    onAbout: () -> Unit = {},
    onExportLogs: (suspend () -> Boolean)? = null
) {
    val background = if (t.isLight) Color(0xFFF5F9FF) else Color(0xFF0F172A)

    Box(Modifier.fillMaxSize().background(background)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 54.dp, bottom = 36.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("‹", style = DW.HeadlineMedium.copy(color = t.ob), modifier = Modifier.clickable(onClick = onBack).padding(8.dp))
                Spacer(Modifier.width(8.dp))
                Text("设置", style = DW.TitleLarge.copy(color = t.ob))
            }

            SectionLabel(t, "外观")
            SettingsGlass(t) {
                ThemeChoice(t, "跟随系统", ThemeState.darkMode == 0) { ThemeState.update(0) }
                ThemeChoice(t, "浅色", ThemeState.darkMode == 1) { ThemeState.update(1) }
                ThemeChoice(t, "深色", ThemeState.darkMode == 2) { ThemeState.update(2) }
            }

            SectionLabel(t, "更多")
            SettingsEntry(t, "联网搜索", "为回复补充最新信息", t.ac, onWebSearch)
            SettingsEntry(t, "关于 ChatMate AI", "版本与隐私说明", t.p, onAbout)
        }
    }
}

@Composable private fun SectionLabel(t: Th, text: String) {
    Text(text, style = DW.LabelSmall.copy(color = t.osv), modifier = Modifier.padding(start = 24.dp, top = 18.dp, bottom = 8.dp))
}

@Composable private fun SettingsGlass(t: Th, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp))
        .background(if (t.isLight) Color.White else Color(0xFF172033)).border(1.dp, t.gb, RoundedCornerShape(22.dp))
        .padding(horizontal = 18.dp, vertical = 10.dp), content = content)
}

@Composable private fun SettingsEntry(t: Th, title: String, subtitle: String, accent: Color, onClick: () -> Unit) {
    Row(Modifier.padding(horizontal = 20.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp))
        .background(if (t.isLight) Color.White else Color(0xFF172033))
        .border(1.dp, accent.copy(alpha = .20f), RoundedCornerShape(22.dp)).clickable(onClick = onClick)
        .padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(accent, RoundedCornerShape(5.dp)))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = DW.TitleMedium.copy(color = t.ob))
            Text(subtitle, style = DW.BodySmall.copy(color = t.osv), maxLines = 1)
        }
        Text("›", style = DW.TitleLarge.copy(color = t.osv))
    }
}

@Composable private fun SettingRow(t: Th, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = DW.BodyLarge.copy(color = t.ob))
            Text(subtitle, style = DW.BodySmall.copy(color = t.osv))
        }
        Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = t.p, checkedThumbColor = Color.White))
    }
}

@Composable private fun ThemeChoice(t: Th, label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(18.dp).border(2.dp, if (selected) t.p else t.osv, RoundedCornerShape(9.dp)).padding(4.dp)) {
            if (selected) Box(Modifier.fillMaxSize().background(t.p, RoundedCornerShape(6.dp)))
        }
        Spacer(Modifier.width(12.dp))
        Text(label, style = DW.BodyMedium.copy(color = if (selected) t.p else t.ob))
    }
}

@Composable
internal fun Sep(t: Th) {
    HorizontalDivider(color = t.gb.copy(alpha = .55f), thickness = .5.dp)
}
