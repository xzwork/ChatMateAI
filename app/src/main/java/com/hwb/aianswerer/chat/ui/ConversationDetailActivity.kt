@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.hwb.aianswerer.chat.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.hwb.aianswerer.BaseActivity
import com.hwb.aianswerer.chat.model.MessageRole
import com.hwb.aianswerer.chat.storage.*
import com.hwb.aianswerer.ui.theme.AIAnswererTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ConversationDetailActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getLongExtra("conversation_id", 0)
        if (id == 0L) { finish(); return }
        val dao = ChatDatabase.get(this).chatDao()
        setContent { AIAnswererTheme {
            var conversation by remember { mutableStateOf<ConversationEntity?>(null) }
            var messages by remember { mutableStateOf(emptyList<MessageEntity>()) }
            var config by remember { mutableStateOf<ConversationAIConfigEntity?>(null) }
            var editingConfig by remember { mutableStateOf(false) }
            var menu by remember { mutableStateOf(false) }
            var showRename by remember { mutableStateOf(false) }
            var rename by remember { mutableStateOf("") }
            var mergeOptions by remember { mutableStateOf<List<ConversationEntity>?>(null) }
            var mergeTarget by remember { mutableStateOf<ConversationEntity?>(null) }
            var error by remember { mutableStateOf("") }
            var deleteDialog by remember { mutableStateOf(false) }
            var selectedMessage by remember { mutableStateOf<MessageEntity?>(null) }
            var messageText by remember { mutableStateOf("") }
            val clipboard = LocalClipboardManager.current
            val scope = rememberCoroutineScope()
            val listState = rememberLazyListState()
            suspend fun reload() {
                conversation = dao.conversation(id)
                messages = dao.messages(id)
                config = dao.aiConfig(id)
                rename = conversation?.displayName.orEmpty()
            }
            LaunchedEffect(id) {
                reload()
                if (messages.isNotEmpty()) listState.scrollToItem(messages.lastIndex)
            }
            if (editingConfig) ConversationConfigScreen(config ?: ConversationAIConfigEntity(id), {
                scope.launch { dao.saveAIConfig(it); config = it; editingConfig = false }
            }, { editingConfig = false }) else Scaffold(
                containerColor = chatBackground(),
                topBar = { TopAppBar(
                    title = { Column {
                        Text(conversation?.displayName ?: "聊天记录", maxLines = 1)
                        Text("${messages.size} 条已读取消息", style = MaterialTheme.typography.labelSmall)
                    } },
                    navigationIcon = { TextButton(onClick = { finish() }) { Text("返回") } },
                    actions = { Box {
                        TextButton(onClick = { menu = true }) { Text("•••") }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(text = { Text("修改备注") }, onClick = { menu = false; showRename = true })
                            DropdownMenuItem(text = { Text("回复偏好与模型") }, onClick = { menu = false; editingConfig = true })
                            DropdownMenuItem(text = { Text("合并联系人") }, onClick = {
                                menu = false
                                scope.launch { mergeOptions = conversation?.let { c -> dao.conversationsForApp(c.appId).filter { it.id != id } }.orEmpty() }
                            })
                            DropdownMenuItem(text = { Text("删除聊天记录", color = MaterialTheme.colorScheme.error) },
                                onClick = { menu = false; deleteDialog = true })
                        }
                    } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = chatBackground())
                ) },
                bottomBar = { Surface(color = MaterialTheme.colorScheme.surface) {
                    Text("长按气泡可复制、修正或删除 · 时间为读取时间", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp))
                } }
            ) { padding ->
                if (messages.isEmpty()) Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("还没有聊天记录，打开聊天后点一下悬浮助手")
                } else LazyColumn(Modifier.padding(padding).fillMaxSize(), state = listState,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    itemsIndexed(messages, key = { _, message -> message.id }) { index, message ->
                        if (index == 0 || kotlin.math.abs(message.timestamp - messages[index - 1].timestamp) >= 5 * 60_000) {
                            Box(Modifier.fillMaxWidth().padding(bottom = 12.dp), contentAlignment = Alignment.Center) {
                                Text(SimpleDateFormat("M月d日 HH:mm", Locale.getDefault()).format(Date(message.timestamp)),
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        ChatBubble(message.content, runCatching { MessageRole.valueOf(message.role) }.getOrDefault(MessageRole.UNKNOWN),
                            conversation?.displayName.orEmpty(), onLongClick = { selectedMessage = message; messageText = message.content })
                    }
                }
            }
            if (error.isNotBlank()) AlertDialog(onDismissRequest = { error = "" }, title = { Text("操作未完成") },
                text = { Text(error) }, confirmButton = { TextButton(onClick = { error = "" }) { Text("知道了") } })
            if (showRename) AlertDialog(onDismissRequest = { showRename = false }, title = { Text("修改备注") },
                text = { OutlinedTextField(rename, { rename = it }, singleLine = true) },
                confirmButton = { TextButton(enabled = rename.isNotBlank(), onClick = { scope.launch {
                    runCatching { dao.renameConversation(id, rename.trim()); reload() }.onFailure { error = "保存失败，请重试" }
                    showRename = false
                } }) { Text("保存") } }, dismissButton = { TextButton(onClick = { showRename = false }) { Text("取消") } })
            mergeOptions?.let { options -> AlertDialog(onDismissRequest = { mergeOptions = null }, title = { Text("合并到哪个联系人？") },
                text = {
                    if (options.isEmpty()) Text("当前 App 没有其他联系人")
                    else LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(options.size) { index -> TextButton(onClick = { mergeTarget = options[index]; mergeOptions = null }) {
                            Text(options[index].displayName)
                        } }
                    }
                }, confirmButton = { TextButton(onClick = { mergeOptions = null }) { Text("取消") } }) }
            mergeTarget?.let { target -> AlertDialog(onDismissRequest = { mergeTarget = null }, title = { Text("合并聊天记录？") },
                text = { Text("将当前记录移入“${target.displayName}”，并移除当前联系人。目标已有的回复偏好会保留。") },
                confirmButton = { TextButton(onClick = { scope.launch {
                    runCatching { dao.mergeConversations(id, target.id) }.onSuccess { finish() }.onFailure { error = "合并失败，请重试" }
                    mergeTarget = null
                } }) { Text("合并") } }, dismissButton = { TextButton(onClick = { mergeTarget = null }) { Text("取消") } }) }
            selectedMessage?.let { message -> AlertDialog(onDismissRequest = { selectedMessage = null }, title = { Text("消息操作") },
                text = { Column {
                    OutlinedTextField(messageText, { messageText = it }, maxLines = 6)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { clipboard.setText(AnnotatedString(messageText)); selectedMessage = null }) { Text("复制") }
                        TextButton(onClick = { scope.launch {
                            dao.updateMessage(message.copy(role = if (message.role == "SELF") "OTHER" else "SELF"))
                            selectedMessage = null; reload()
                        } }) { Text(if (message.role == "SELF") "改为对方" else "改为我") }
                        TextButton(onClick = { scope.launch { dao.deleteMessage(message.id); selectedMessage = null; reload() } }) {
                            Text("删除", color = MaterialTheme.colorScheme.error)
                        }
                    }
                } }, confirmButton = { TextButton(enabled = messageText.isNotBlank(), onClick = { scope.launch {
                    dao.updateMessage(message.copy(content = messageText.trim())); selectedMessage = null; reload()
                } }) { Text("保存") } }, dismissButton = { TextButton(onClick = { selectedMessage = null }) { Text("取消") } }) }
            if (deleteDialog) AlertDialog(onDismissRequest = { deleteDialog = false }, title = { Text("删除会话？") },
                text = { Text("该会话的聊天历史和个性化配置将被永久删除。") },
                confirmButton = { TextButton(onClick = { scope.launch { dao.deleteConversation(id); finish() } }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                } }, dismissButton = { TextButton(onClick = { deleteDialog = false }) { Text("取消") } })
        } }
    }
}
