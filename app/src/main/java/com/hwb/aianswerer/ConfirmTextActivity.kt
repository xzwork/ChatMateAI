package com.hwb.aianswerer

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.hwb.aianswerer.config.AppConfig
import com.hwb.aianswerer.ui.components.AnimatedButton
import com.hwb.aianswerer.ui.components.AppTextField
import com.hwb.aianswerer.ui.components.ButtonVariant
import com.hwb.aianswerer.ui.theme.*

/** Shows recognition first. Generating content is always an explicit user action. */
class ConfirmTextActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val recognizedText = intent.getStringExtra(Constants.EXTRA_RECOGNIZED_TEXT).orEmpty()
        val fromScreenText = intent.getBooleanExtra(Constants.EXTRA_FROM_SCREEN_TEXT, false)
        setContent {
            AIAnswererTheme {
                RecognitionResultScreen(recognizedText, fromScreenText, ::handleGenerate, ::retryWithScreenshot, ::finish)
            }
        }
    }

    private fun retryWithScreenshot() {
        sendBroadcast(Intent(Constants.ACTION_RECOGNIZE_WITH_SCREENSHOT).setPackage(packageName))
        finish()
    }

    private fun handleGenerate(text: String) {
        if (text.isBlank()) {
            Toast.makeText(this, "识别内容不能为空", Toast.LENGTH_SHORT).show()
            return
        }
        if (!AppConfig.isApiConfigValid()) {
            Toast.makeText(this, "生成内容前，请先配置 OpenAI 兼容模型", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, ModelSettingsActivity::class.java))
            return
        }
        sendBroadcast(Intent(Constants.ACTION_REQUEST_ANSWER).apply {
            setPackage(packageName)
            putExtra(Constants.EXTRA_QUESTION_TEXT, text)
        })
        finish()
    }
}

@Composable
private fun RecognitionResultScreen(
    recognizedText: String,
    fromScreenText: Boolean,
    onGenerate: (String) -> Unit,
    onRetryWithScreenshot: () -> Unit,
    onCancel: () -> Unit
) {
    var text by remember { mutableStateOf(recognizedText) }
    val sections = remember(text) { splitRecognitionResults(text) }
    val isDark = LocalIsDarkMode.current
    val bg = if (isDark) Color(0xFF0F172A) else Color(0xFFF5F9FF)

    Column(Modifier.fillMaxSize().background(bg).padding(top = 52.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("识别结果", style = DW.TitleLarge.copy(color = if (isDark) TextDarkPrimary else TextDark))
                Text("确认无误后再开始生成", style = DW.BodySmall.copy(color = if (isDark) TextDarkSecondary else TextSecondary))
            }
            Text("${sections.size} 项", style = DW.LabelMedium.copy(color = PremiumPrimary))
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            sections.forEachIndexed { index, section ->
                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(18.dp))
                    .background(if (isDark) GlassDark else GlassWhite)
                    .border(1.dp, if (isDark) GlassDarkBorder else GlassWhiteBorder, RoundedCornerShape(18.dp))
                    .padding(16.dp)) {
                    Text("${index + 1}".padStart(2, '0'), style = DW.LabelSmall.copy(color = PremiumPrimary), modifier = Modifier.width(34.dp))
                    Text(section, style = DW.BodyMedium.copy(color = if (isDark) TextDarkPrimary else TextDark), modifier = Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(4.dp))
            AppTextField(text, { text = it }, "可编辑识别文本", "检查或修正识别结果", singleLine = false, maxLines = 12)
            if (fromScreenText) {
                Spacer(Modifier.height(12.dp))
                AnimatedButton("使用智能截图重新识别", onRetryWithScreenshot, variant = ButtonVariant.Tonal)
            }
            Spacer(Modifier.height(18.dp))
        }

        Row(Modifier.fillMaxWidth().background(if (isDark) Color.Black.copy(alpha = .18f) else Color.White.copy(alpha = .38f))
            .padding(horizontal = 20.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AnimatedButton("取消", onCancel, Modifier.weight(.8f), ButtonVariant.Glass)
            AnimatedButton("开始生成", { onGenerate(text) }, Modifier.weight(1.2f), ButtonVariant.Primary)
        }
    }
}

internal fun splitRecognitionResults(text: String): List<String> {
    val normalized = text.trim()
    if (normalized.isBlank()) return emptyList()
    val blankLineParts = normalized.split(Regex("\\n\\s*\\n+")).map(String::trim).filter(String::isNotBlank)
    if (blankLineParts.size > 1) return blankLineParts
    val numbered = normalized.split(Regex("(?m)(?=^\\s*(?:第\\s*\\d+\\s*题|\\d+[.、)]))"))
        .map(String::trim).filter(String::isNotBlank)
    return numbered.ifEmpty { listOf(normalized) }
}
