@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.hwb.aianswerer.chat.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hwb.aianswerer.BaseActivity
import com.hwb.aianswerer.Constants
import com.hwb.aianswerer.ModelSettingsActivity
import com.hwb.aianswerer.chat.ChatSession
import com.hwb.aianswerer.chat.ai.*
import com.hwb.aianswerer.chat.model.ChatMessage
import com.hwb.aianswerer.chat.model.MessageRole
import com.hwb.aianswerer.chat.storage.ChatDatabase
import com.hwb.aianswerer.chat.storage.ConversationAIConfigEntity
import com.hwb.aianswerer.chat.storage.MessageEntity
import com.hwb.aianswerer.ui.theme.AIAnswererTheme
import kotlinx.coroutines.*

class ChatCompanionActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val session = ChatSession.active ?: run { finish(); return }
        val dao = ChatDatabase.get(this).chatDao()
        setContent { AIAnswererTheme {
            val recognized = remember { mutableStateListOf(*session.screen.messages.toTypedArray()) }
            var intentText by rememberSaveable { mutableStateOf("") }
            var persistPurpose by remember { mutableStateOf(false) }
            var loaded by remember { mutableStateOf(false) }
            var config by remember { mutableStateOf<ConversationAIConfigEntity?>(null) }
            var output by rememberSaveable { mutableStateOf("") }
            var streaming by remember { mutableStateOf("") }
            var error by remember { mutableStateOf("") }
            var loading by remember { mutableStateOf(false) }
            var expanded by remember { mutableStateOf(false) }
            var showIntent by remember { mutableStateOf(false) }
            var selectedStyle by rememberSaveable { mutableStateOf(ReplyStyle.NATURAL) }
            var editIndex by remember { mutableStateOf<Int?>(null) }
            var editText by remember { mutableStateOf("") }
            var editRole by remember { mutableStateOf(MessageRole.OTHER) }
            var generation by remember { mutableStateOf<Job?>(null) }
            var autoStarted by rememberSaveable { mutableStateOf(false) }
            val scope = rememberCoroutineScope()
            val clipboard = LocalClipboardManager.current
            val client = remember { ChatAIClient() }
            val canGenerate = recognized.any { it.content.isNotBlank() && it.role in setOf(MessageRole.SELF, MessageRole.OTHER) }

            LaunchedEffect(session.conversationId) {
                config = dao.aiConfig(session.conversationId)
                persistPurpose = config?.persistPurpose == true
                if (savedInstanceState == null) intentText = if (persistPurpose) config?.purpose.orEmpty() else ""
                loaded = true
            }
            LaunchedEffect(intentText, persistPurpose, loaded) {
                if (loaded) {
                    delay(250)
                    val updated = (config ?: ConversationAIConfigEntity(session.conversationId)).copy(
                        persistPurpose = persistPurpose, purpose = if (persistPurpose) intentText else "")
                    dao.saveAIConfig(updated)
                    config = updated
                }
            }
            fun generate(automatic: Boolean = false) {
                if (loading || !loaded || !canGenerate) return
                val resolved = AIConfigResolver.resolve(GlobalAIConfigStore.read(this@ChatCompanionActivity), config)
                if (resolved.apiKey.isBlank() || resolved.model.isBlank() || !resolved.apiBaseUrl.startsWith("http")) {
                    error = "先配置模型，即可生成回复"
                    if (!automatic) startActivity(Intent(this@ChatCompanionActivity, ModelSettingsActivity::class.java))
                    return
                }
                val previous = output
                val request = PromptBuilder.build(resolved.systemPrompt, session.screen.conversation,
                    mergeEditedScreenMessages(session.messages, session.screen.messages, recognized.toList()),
                    intentText, selectedStyle, previous)
                loading = true
                error = ""
                streaming = ""
                generation = scope.launch {
                    try {
                        client.generate(resolved, request) { partial ->
                            withContext(Dispatchers.Main.immediate) { streaming = partial }
                        }.onSuccess { output = it }.onFailure {
                            output = previous
                            error = "生成失败，请重试或检查模型设置"
                        }
                    } finally { loading = false }
                }
            }
            LaunchedEffect(loaded) {
                if (loaded && !autoStarted && output.isBlank()) {
                    autoStarted = true
                    if (recognized.none { it.role == MessageRole.UNKNOWN } &&
                        recognized.lastOrNull()?.role == MessageRole.OTHER) generate(automatic = true)
                }
            }
            val background = chatBackground()
            Scaffold(containerColor = background,
                topBar = { TopAppBar(
                    title = { Column {
                        Text(session.screen.conversation.displayName, maxLines = 1, fontWeight = FontWeight.SemiBold)
                        Text("${session.screen.conversation.appName} · 已读 ${recognized.size} 条", style = MaterialTheme.typography.labelSmall)
                    } },
                    navigationIcon = { TextButton(onClick = { finish() }) { Text("返回") } },
                    actions = { TextButton(onClick = {
                        startActivity(Intent(this@ChatCompanionActivity, ConversationDetailActivity::class.java)
                            .putExtra("conversation_id", session.conversationId))
                    }) { Text("历史") } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = background)
                ) },
                bottomBar = {
                    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp) {
                        Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("推荐回复", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                if (loading) TextButton(onClick = { generation?.cancel(); error = "已停止，可重新生成" }) { Text("停止") }
                                else TextButton(enabled = canGenerate && loaded, onClick = { generate() }) {
                                    Text(if (output.isBlank()) "帮我回复" else "换一句")
                                }
                            }
                            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            if ((if (loading) streaming else output).isNotBlank()) OutlinedTextField(if (loading) streaming else output, { output = it }, readOnly = loading,
                                modifier = Modifier.fillMaxWidth().heightIn(max = 160.dp), maxLines = 5,
                                shape = RoundedCornerShape(12.dp))
                            else Text(if (loading) "正在想一句合适的回复…" else "选个语气，帮你接住这句话",
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(enabled = output.isNotBlank() && !loading, onClick = {
                                    clipboard.setText(AnnotatedString(output.trim()))
                                    Toast.makeText(this@ChatCompanionActivity, "已复制", Toast.LENGTH_SHORT).show()
                                }) { Text("复制") }
                                Button(enabled = output.isNotBlank() && !loading, modifier = Modifier.weight(1f), onClick = {
                                    clipboard.setText(AnnotatedString(output.trim()))
                                    Toast.makeText(this@ChatCompanionActivity, "已复制，粘贴后即可发送", Toast.LENGTH_SHORT).show()
                                    finish()
                                }) { Text("复制并返回聊天") }
                            }
                        }
                    }
                }
            ) { padding ->
                LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.weight(1f)) {
                            Text(if (expanded) "收起上下文" else "最近消息 · 展开全部 ${recognized.size} 条")
                        }
                        TextButton(enabled = !loading, onClick = {
                            sendBroadcast(Intent(Constants.ACTION_RECOGNIZE_WITH_SCREENSHOT).setPackage(packageName))
                            finish()
                        }) { Text("重新读取") }
                    } }
                    val start = if (expanded) 0 else (recognized.size - 3).coerceAtLeast(0)
                    items(recognized.size - start) { offset ->
                        val index = start + offset
                        val message = recognized[index]
                        ChatBubble(message.content, message.role, session.screen.conversation.displayName, onClick = {
                            if (!loading) { editIndex = index; editText = message.content; editRole = message.role }
                        })
                    }
                    item { Text("点按消息可修正内容和身份", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    if (recognized.any { it.role == MessageRole.UNKNOWN }) item {
                        Text("有消息身份不确定，点按气泡确认后回复更准确", color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall)
                    }
                    if (recognized.lastOrNull()?.role == MessageRole.SELF) item {
                        Text("最近一句是你发的，可以先等等对方；需要补充时再生成。", style = MaterialTheme.typography.bodySmall)
                    }
                    item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReplyStyle.entries.forEach { style -> FilterChip(selected = selectedStyle == style, enabled = !loading,
                            onClick = { selectedStyle = style; generate() }, label = { Text(style.label) }) }
                    } }
                    item {
                        TextButton(onClick = { showIntent = !showIntent }) { Text(if (showIntent) "收起补充要求" else "补充想法（选填）") }
                        if (showIntent) {
                            OutlinedTextField(intentText, { intentText = it }, enabled = !loading,
                                label = { Text("想怎么回？") }, placeholder = { Text("例如：周末想约她喝咖啡，别太刻意") },
                                modifier = Modifier.fillMaxWidth(), maxLines = 3, shape = RoundedCornerShape(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(persistPurpose, { persistPurpose = it }, enabled = loaded)
                                Text("记住这个聊天的要求", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            editIndex?.let { index -> AlertDialog(onDismissRequest = { editIndex = null }, title = { Text("修正消息") },
                text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(editRole == MessageRole.OTHER, { editRole = MessageRole.OTHER }, label = { Text("对方") })
                        FilterChip(editRole == MessageRole.SELF, { editRole = MessageRole.SELF }, label = { Text("我") })
                        FilterChip(editRole == MessageRole.SYSTEM, { editRole = MessageRole.SYSTEM }, label = { Text("忽略") })
                    }
                    OutlinedTextField(editText, { editText = it }, maxLines = 6)
                } }, confirmButton = { TextButton(enabled = editText.isNotBlank(), onClick = {
                    val edited = recognized.toMutableList().apply {
                        this[index] = this[index].copy(content = editText.trim(), role = editRole)
                    }
                    scope.launch {
                        runCatching {
                            mergeEditedScreenMessages(session.messages, session.screen.messages, edited)
                                .filter { it.id != 0L }.forEach { message ->
                                    val original = session.messages.firstOrNull { it.id == message.id }
                                    if (original != null && (original.content != message.content || original.role != message.role)) {
                                        dao.updateMessage(MessageEntity(message.id, session.conversationId, message.role.name,
                                            message.content, message.timestamp, message.source.name))
                                    }
                                }
                        }.onSuccess {
                            recognized[index] = edited[index]
                            output = ""
                            editIndex = null
                        }.onFailure { error = "修正保存失败，请重试" }
                    }
                }) { Text("保存") } }, dismissButton = { TextButton(onClick = { editIndex = null }) { Text("取消") } }) }
        } }
    }
}

internal fun mergeEditedScreenMessages(history: List<ChatMessage>, originalScreen: List<ChatMessage>,
                                       editedScreen: List<ChatMessage>): List<ChatMessage> {
    if (originalScreen.isEmpty()) return history + editedScreen
    fun signature(message: ChatMessage) = "${message.role}:${message.content.trim().replace(Regex("\\s+"), " ")}"
    val original = originalScreen.map(::signature)
    val existing = history.map(::signature)
    // A captured page may be an earlier slice of history, not necessarily the newest page.
    val position = (0..(history.size - originalScreen.size)).lastOrNull { start ->
        existing.subList(start, start + original.size) == original
    }
    if (position != null) return history.take(position) + editedScreen.mapIndexed { index, message ->
        message.copy(id = history[position + index].id, conversationId = history[position + index].conversationId)
    } + history.drop(position + original.size)
    val overlap = (minOf(history.size, original.size) downTo 1).firstOrNull { size ->
        existing.takeLast(size) == original.take(size)
    } ?: 0
    return history.dropLast(overlap) + editedScreen.mapIndexed { index, message ->
        if (index < overlap) message.copy(id = history[history.size - overlap + index].id,
            conversationId = history[history.size - overlap + index].conversationId) else message
    }
}
