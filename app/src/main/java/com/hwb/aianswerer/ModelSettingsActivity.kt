package com.hwb.aianswerer

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.hwb.aianswerer.api.OpenAIClient
import com.hwb.aianswerer.config.AppConfig
import com.hwb.aianswerer.ui.components.*
import com.hwb.aianswerer.ui.pages.TestState
import com.hwb.aianswerer.ui.theme.*
import com.google.gson.JsonParser
import kotlinx.coroutines.launch

private val MODEL_CONFIG_JSON_TEMPLATE = """
    {
      "apiUrl": "https://api.openai.com/v1/chat/completions",
      "apiKey": "sk-your-api-key",
      "model": "gpt-4.1-mini"
    }
""".trimIndent()

class ModelSettingsActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AIAnswererTheme {
                CustomModelScreen(
                    onBack = { finish() },
                    onSaved = { Toast.makeText(this, "配置已保存", Toast.LENGTH_SHORT).show() }
                )
            }
        }
    }
}

@Composable
private fun CustomModelScreen(onBack: () -> Unit, onSaved: () -> Unit) {
    var apiUrl by remember { mutableStateOf(AppConfig.getApiUrl()) }
    var apiKey by remember { mutableStateOf(AppConfig.getApiKey()) }
    var model by remember { mutableStateOf(AppConfig.getModelName()) }
    var jsonConfig by remember { mutableStateOf(MODEL_CONFIG_JSON_TEMPLATE) }
    var testState by remember { mutableStateOf<TestState>(TestState.Idle) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val isDark = LocalIsDarkMode.current
    val bg = if (isDark) androidx.compose.ui.graphics.Color(0xFF0F172A) else androidx.compose.ui.graphics.Color(0xFFF5F9FF)

    Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = { TopBarWithBack("OpenAI 兼容模型", onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().background(bg)
                .verticalScroll(rememberScrollState()).padding(padding)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            InfoCard {
                AppTextField(apiUrl, { apiUrl = it }, "API 地址", "https://api.openai.com/v1/chat/completions")
                Spacer(Modifier.height(12.dp))
                PasswordTextField(apiKey, { apiKey = it }, "API Key", "sk-…")
                Spacer(Modifier.height(12.dp))
                AppTextField(model, { model = it }, "模型名称", "gpt-4.1-mini")
                Spacer(Modifier.height(16.dp))

                AnimatedButton(
                    text = if (testState is TestState.Testing) "正在测试…" else "测试连接",
                    onClick = {
                        if (apiUrl.isBlank() || apiKey.isBlank() || model.isBlank()) {
                            testState = TestState.Error("请填写完整配置")
                        } else scope.launch {
                            testState = TestState.Testing
                            testState = OpenAIClient.getInstance().testConnection(apiUrl, apiKey, model).fold(
                                { TestState.Success() }, { TestState.Error(it.message ?: "连接失败") }
                            )
                        }
                    }, variant = ButtonVariant.Tonal, enabled = testState !is TestState.Testing
                )
                when (val state = testState) {
                    is TestState.Success -> Text("连接成功", color = SuccessGreen, modifier = Modifier.padding(top = 8.dp))
                    is TestState.Error -> Text(state.msg, color = ErrorRed, modifier = Modifier.padding(top = 8.dp))
                    else -> Unit
                }
                Spacer(Modifier.height(12.dp))
                AnimatedButton("保存配置", onClick = {
                    if (apiUrl.isBlank() || apiKey.isBlank() || model.isBlank() || !apiUrl.startsWith("http")) {
                        testState = TestState.Error("请填写有效的 API 地址、Key 和模型名称")
                    } else {
                        AppConfig.saveApiUrl(apiUrl.trim())
                        AppConfig.saveApiKey(apiKey.trim())
                        AppConfig.saveModelName(model.trim())
                        onSaved()
                    }
                })
            }

            InfoCard {
                Text("JSON 导入", style = DW.TitleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "粘贴符合模板的配置后导入到上方。API Key 只在本机解析和保存，请勿分享包含真实 Key 的 JSON。",
                    style = DW.BodySmall
                )
                Spacer(Modifier.height(12.dp))
                AppTextField(
                    value = jsonConfig,
                    onValueChange = { jsonConfig = it },
                    label = "模型配置 JSON",
                    placeholder = MODEL_CONFIG_JSON_TEMPLATE,
                    singleLine = false,
                    maxLines = 12,
                    modifier = Modifier.heightIn(min = 180.dp)
                )
                Spacer(Modifier.height(12.dp))
                AnimatedButton(
                    text = "复制 JSON 模板",
                    onClick = {
                        clipboard.setText(AnnotatedString(MODEL_CONFIG_JSON_TEMPLATE))
                        Toast.makeText(context, "JSON 模板已复制", Toast.LENGTH_SHORT).show()
                    },
                    variant = ButtonVariant.Tonal
                )
                Spacer(Modifier.height(10.dp))
                AnimatedButton(
                    text = "导入到上方配置",
                    onClick = {
                        runCatching {
                            val root = JsonParser.parseString(jsonConfig)
                            require(root.isJsonObject) { "根节点必须是 JSON 对象" }
                            val obj = root.asJsonObject
                            fun requiredString(name: String): String {
                                val element = obj.get(name)
                                require(element != null && element.isJsonPrimitive && element.asJsonPrimitive.isString) {
                                    "缺少字符串字段：$name"
                                }
                                return element.asString.trim().also {
                                    require(it.isNotEmpty()) { "字段不能为空：$name" }
                                }
                            }
                            val importedUrl = requiredString("apiUrl")
                            require(importedUrl.startsWith("http://") || importedUrl.startsWith("https://")) {
                                "apiUrl 必须以 http:// 或 https:// 开头"
                            }
                            Triple(importedUrl, requiredString("apiKey"), requiredString("model"))
                        }.onSuccess { imported ->
                            apiUrl = imported.first
                            apiKey = imported.second
                            model = imported.third
                            jsonConfig = MODEL_CONFIG_JSON_TEMPLATE
                            testState = TestState.Idle
                            Toast.makeText(context, "JSON 已导入，请确认后保存", Toast.LENGTH_SHORT).show()
                        }.onFailure { error ->
                            testState = TestState.Error("JSON 导入失败：${error.message ?: "格式不正确"}")
                        }
                    }
                )
            }
        }
    }
}
