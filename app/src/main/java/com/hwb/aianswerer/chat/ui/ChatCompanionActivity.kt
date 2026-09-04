@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.hwb.aianswerer.chat.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.luminance
import androidx.lifecycle.lifecycleScope
import com.hwb.aianswerer.BaseActivity
import com.hwb.aianswerer.Constants
import com.hwb.aianswerer.ModelSettingsActivity
import com.hwb.aianswerer.chat.ChatSession
import com.hwb.aianswerer.chat.ai.*
import com.hwb.aianswerer.chat.model.ChatMessage
import com.hwb.aianswerer.chat.model.MessageRole
import com.hwb.aianswerer.chat.storage.ChatDatabase
import com.hwb.aianswerer.chat.storage.ConversationAIConfigEntity
import com.hwb.aianswerer.ui.theme.AIAnswererTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatCompanionActivity : BaseActivity() {
    private var purposeSaveJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val session = ChatSession.active ?: run { finish(); return }
        val dao = ChatDatabase.get(this).chatDao()

        setContent {
            AIAnswererTheme {
                val recognizedMessages = remember {
                    mutableStateListOf(*session.screen.messages.toTypedArray())
                }
                var intentText by remember { mutableStateOf("") }
                var persistPurpose by remember { mutableStateOf(false) }
                var purposeStateLoaded by remember { mutableStateOf(false) }
                var output by remember { mutableStateOf("") }
                var error by remember { mutableStateOf("") }
                var loading by remember { mutableStateOf(false) }
                var localConfig by remember { mutableStateOf<ConversationAIConfigEntity?>(null) }
                val clipboard = LocalClipboardManager.current
                LaunchedEffect(session.conversationId) {
                    localConfig = dao.aiConfig(session.conversationId)
                    persistPurpose = localConfig?.persistPurpose == true
                    intentText = if (persistPurpose) localConfig?.purpose.orEmpty() else ""
                    purposeStateLoaded = true
                }

                fun savePurposePreference(enabled: Boolean, text: String, debounce: Boolean) {
                    if (!purposeStateLoaded) return
                    val updated = (localConfig ?: ConversationAIConfigEntity(session.conversationId)).copy(
                        persistPurpose = enabled,
                        purpose = if (enabled) text else ""
                    )
                    localConfig = updated
                    purposeSaveJob?.cancel()
                    purposeSaveJob = lifecycleScope.launch {
                        if (debounce) delay(250)
                        dao.saveAIConfig(updated)
                    }
                }

                fun generate() {
                    if (persistPurpose) savePurposePreference(true, intentText, debounce = false)
                    val config = AIConfigResolver.resolve(GlobalAIConfigStore.read(this@ChatCompanionActivity), localConfig)
                    if (config.apiBaseUrl.isBlank() || !config.apiBaseUrl.startsWith("http") ||
                        config.apiKey.isBlank() || config.model.isBlank()
                    ) {
                        startActivity(Intent(this@ChatCompanionActivity, ModelSettingsActivity::class.java))
                        return
                    }
                    loading = true
                    error = ""
                    output = ""
                    lifecycleScope.launch {
                        val contextMessages = mergeEditedScreenMessages(
                            session.messages,
                            session.screen.messages,
                            recognizedMessages.toList()
                        )
                        val requestMessages = PromptBuilder.build(
                            config.systemPrompt,
                            session.screen.conversation,
                            contextMessages,
                            intentText
                        )
                        ChatAIClient().generate(config, requestMessages) { partial ->
                            withContext(Dispatchers.Main.immediate) {
                                output = partial
                            }
                        }
                            .onSuccess { output = it }
                            .onFailure {
                                val message = it.message.orEmpty()
                                error = if (message.contains("timeout", ignoreCase = true) ||
                                    message.contains("timed out", ignoreCase = true)
                                ) {
                                    "生成超时（最长等待 5 分钟），请重试或换用更快的模型"
                                } else {
                                    message.ifBlank { "生成失败" }
                                }
                            }
                        loading = false
                    }
                }

                val pageBg = if (MaterialTheme.colorScheme.background.luminance() > .5f)
                    androidx.compose.ui.graphics.Color(0xFFF5F9FF) else androidx.compose.ui.graphics.Color(0xFF0F172A)

                Scaffold(
                    containerColor = pageBg,
                    topBar = {
                        TopAppBar(
                            title = {
                                Column {
                                    Text(session.screen.conversation.displayName, fontWeight = FontWeight.SemiBold)
                                    Text("已识别 ${recognizedMessages.size} 条消息", style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            },
                            navigationIcon = { TextButton(onClick = { finish() }) { Text("关闭") } },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = pageBg)
                        )
                    },
                    bottomBar = {
                        GenerationDock(
                            loading = loading,
                            output = output,
                            error = error,
                            canGenerate = recognizedMessages.any {
                                it.content.isNotBlank() && (it.role == MessageRole.SELF || it.role == MessageRole.OTHER)
                            },
                            onGenerate = { generate() },
                            onCopy = { clipboard.setText(AnnotatedString(output)) }
                        )
                    }
                ) { padding ->
                    Column(
                        Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())
                            .padding(horizontal = 18.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(28.dp),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 0.dp,
                            shadowElevation = 1.dp
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("聊天内容", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                        Text("直接编辑气泡；点击身份标签可以切换对话双方",
                                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }

                                OutlinedButton(
                                    onClick = {
                                        sendBroadcast(Intent(Constants.ACTION_RECOGNIZE_WITH_SCREENSHOT).setPackage(packageName))
                                        finish()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp)
                                ) { Text(if (session.usedScreenshot) "重新截图识别" else "改用截图识别") }

                                recognizedMessages.forEachIndexed { index, message ->
                                    EditableMessageBubble(message = message, onChange = { recognizedMessages[index] = it })
                                }
                            }
                        }

                        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
                            Column(Modifier.padding(16.dp)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text("本次生成意图", style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                    FilterChip(
                                        selected = persistPurpose,
                                        enabled = purposeStateLoaded,
                                        onClick = {
                                            val enabled = !persistPurpose
                                            persistPurpose = enabled
                                            savePurposePreference(enabled, intentText, debounce = false)
                                        },
                                        label = { Text(if (persistPurpose) "已固定" else "固定") }
                                    )
                                }
                                Text(
                                    if (persistPurpose) "选填 · 已固定到当前聊天" else "选填 · 仅用于本次生成",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = intentText,
                                    onValueChange = {
                                        intentText = it
                                        if (persistPurpose) {
                                            savePurposePreference(true, it, debounce = true)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = { Text("例如：礼貌拒绝，但保持轻松语气") },
                                    minLines = 2,
                                    shape = RoundedCornerShape(18.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun GenerationDock(
    loading: Boolean,
    output: String,
    error: String,
    canGenerate: Boolean,
    onGenerate: () -> Unit,
    onCopy: () -> Unit
) {
    val outputScroll = rememberScrollState()
    LaunchedEffect(output) {
        if (output.isNotEmpty()) outputScroll.scrollTo(outputScroll.maxValue)
    }
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 10.dp,
        tonalElevation = 2.dp
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (error.isNotBlank()) {
                Text(
                    error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
            if (loading || output.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("推荐回复", style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    if (loading) {
                        Text("实时生成中", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary)
                    } else {
                        TextButton(onClick = onCopy, contentPadding = PaddingValues(horizontal = 8.dp)) {
                            Text("复制")
                        }
                    }
                }
                if (loading && output.isBlank()) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("正在连接模型…", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(
                        output + if (loading) " ▌" else "",
                        modifier = Modifier.fillMaxWidth().heightIn(max = 170.dp).verticalScroll(outputScroll),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
            Button(
                onClick = onGenerate,
                enabled = !loading && canGenerate,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(if (loading) "生成中…" else if (output.isBlank()) "开始生成" else "重新生成")
            }
        }
    }
}

@Composable
private fun EditableMessageBubble(message: ChatMessage, onChange: (ChatMessage) -> Unit) {
    val isSelf = message.role == MessageRole.SELF
    val identityLabel = when (message.role) {
        MessageRole.SELF -> "我 · 点击切换为对方"
        MessageRole.OTHER -> "对方 · 点击切换为我"
        MessageRole.SYSTEM -> "系统内容 · 点击标记为我"
        MessageRole.UNKNOWN -> "身份未识别 · 点击标记为我"
    }
    val bubbleColor = if (isSelf) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val horizontal = if (isSelf) Arrangement.End else Arrangement.Start

    Row(Modifier.fillMaxWidth(), horizontalArrangement = horizontal) {
        Column(Modifier.fillMaxWidth(.86f), horizontalAlignment = if (isSelf) Alignment.End else Alignment.Start) {
            Text(
                identityLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable {
                    onChange(message.copy(role = if (isSelf) MessageRole.OTHER else MessageRole.SELF))
                }.padding(horizontal = 8.dp, vertical = 4.dp)
            )
            OutlinedTextField(
                value = message.content,
                onValueChange = { onChange(message.copy(content = it)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = bubbleColor,
                    unfocusedContainerColor = bubbleColor,
                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = .35f),
                    unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent
                ),
                minLines = 1,
                maxLines = 8
            )
        }
    }
}

private fun mergeEditedScreenMessages(
    history: List<ChatMessage>,
    originalScreen: List<ChatMessage>,
    editedScreen: List<ChatMessage>
): List<ChatMessage> {
    if (originalScreen.isEmpty()) return history
    val maximumOverlap = minOf(history.size, originalScreen.size)
    val overlap = (maximumOverlap downTo 1).firstOrNull { size ->
        history.takeLast(size).map(::messageSignature) == originalScreen.takeLast(size).map(::messageSignature)
    } ?: 0
    return if (overlap > 0) {
        history.dropLast(overlap) + editedScreen.takeLast(overlap)
    } else {
        history + editedScreen
    }
}

private fun messageSignature(message: ChatMessage): String =
    "${message.role}:${message.content.trim().replace(Regex("\\s+"), " ")}"
