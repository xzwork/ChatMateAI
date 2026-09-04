@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.hwb.aianswerer.chat.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hwb.aianswerer.BaseActivity
import com.hwb.aianswerer.chat.ai.GlobalAIConfigStore
import com.hwb.aianswerer.ui.theme.AIAnswererTheme

class SystemPromptSettingsActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initial = GlobalAIConfigStore.read(this)
        setContent {
            AIAnswererTheme {
                var prompt by rememberSaveable { mutableStateOf(initial.systemPrompt) }
                var temperature by rememberSaveable { mutableStateOf(initial.temperature.toString()) }
                var maxTokens by rememberSaveable { mutableStateOf(initial.maxTokens.toString()) }
                val background = MaterialTheme.colorScheme.background

                Scaffold(
                    containerColor = background,
                    topBar = {
                        TopAppBar(
                            title = { Text("系统提示词", fontWeight = FontWeight.SemiBold) },
                            navigationIcon = { TextButton(onClick = { finish() }) { Text("返回") } },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = background)
                        )
                    }
                ) { padding ->
                    Column(
                        Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(26.dp),
                            color = Color.Transparent
                        ) {
                            Column(
                                Modifier.background(
                                    Brush.linearGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primaryContainer,
                                            MaterialTheme.colorScheme.tertiaryContainer
                                        )
                                    )
                                ).padding(20.dp)
                            ) {
                                Text("定义默认回复方式", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "所有聊天默认继承这里的规则，也可以在单个会话中覆盖。",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                OutlinedTextField(
                                    value = prompt,
                                    onValueChange = { prompt = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("系统提示词") },
                                    placeholder = { Text("例如：回复自然、简洁，避免正式书面语") },
                                    minLines = 7,
                                    shape = RoundedCornerShape(18.dp)
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    OutlinedTextField(
                                        value = temperature,
                                        onValueChange = { temperature = it },
                                        modifier = Modifier.weight(1f),
                                        label = { Text("随机性") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    OutlinedTextField(
                                        value = maxTokens,
                                        onValueChange = { maxTokens = it.filter(Char::isDigit) },
                                        modifier = Modifier.weight(1f),
                                        label = { Text("输出长度") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = {
                                GlobalAIConfigStore.save(
                                    this@SystemPromptSettingsActivity,
                                    initial.copy(
                                        systemPrompt = prompt,
                                        temperature = temperature.toDoubleOrNull()?.coerceIn(0.0, 2.0) ?: initial.temperature,
                                        maxTokens = maxTokens.toIntOrNull()?.coerceIn(64, 32768) ?: initial.maxTokens
                                    )
                                )
                                Toast.makeText(this@SystemPromptSettingsActivity, "系统提示词已保存", Toast.LENGTH_SHORT).show()
                                finish()
                            },
                            modifier = Modifier.fillMaxWidth().height(54.dp),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Text("保存设置", fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(18.dp))
                    }
                }
            }
        }
    }
}
