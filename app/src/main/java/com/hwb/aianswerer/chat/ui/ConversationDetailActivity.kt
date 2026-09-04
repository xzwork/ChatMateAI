@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.hwb.aianswerer.chat.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.hwb.aianswerer.BaseActivity
import com.hwb.aianswerer.chat.storage.*
import com.hwb.aianswerer.ui.theme.AIAnswererTheme
import kotlinx.coroutines.launch

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
            var rename by remember { mutableStateOf("") }
            var mergeTarget by remember { mutableStateOf("") }
            var mergeError by remember { mutableStateOf("") }
            var showDeleteDialog by remember { mutableStateOf(false) }
            suspend fun reload() { conversation = dao.conversation(id); messages = dao.messages(id); config = dao.aiConfig(id); rename = conversation?.displayName.orEmpty() }
            LaunchedEffect(Unit) { reload() }
            if (editingConfig) ConversationConfigScreen(config ?: ConversationAIConfigEntity(id), {
                lifecycleScope.launch { dao.saveAIConfig(it); config = it; editingConfig = false }
            }, { editingConfig = false }) else Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                topBar = { TopAppBar(
                    title = { Text(conversation?.displayName ?: "会话") },
                    navigationIcon = { TextButton(onClick = { finish() }) { Text("返回") } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                ) }
            ) { padding ->
                Column(Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(rename, { rename = it }, label = { Text("联系人名称") }, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { lifecycleScope.launch { if (rename.isNotBlank()) { dao.renameConversation(id, rename.trim()); reload() } } }) { Text("保存名称") }
                        OutlinedButton(onClick = { editingConfig = true }) { Text("AI 配置") }
                        OutlinedButton(onClick = { showDeleteDialog = true }) { Text("删除会话", color = MaterialTheme.colorScheme.error) }
                    }
                    OutlinedTextField(
                        mergeTarget, { mergeTarget = it.filter(Char::isDigit) },
                        label = { Text("合并到会话 ID") }, modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                    OutlinedButton(onClick = {
                        val targetId = mergeTarget.toLongOrNull()
                        if (targetId == null || targetId == id) mergeError = "请输入其他有效会话 ID"
                        else lifecycleScope.launch {
                            runCatching { dao.mergeConversations(id, targetId) }
                                .onSuccess { finish() }
                                .onFailure { mergeError = "合并失败：目标会话不存在" }
                        }
                    }) { Text("合并联系人") }
                    if (mergeError.isNotBlank()) Text(mergeError, color = MaterialTheme.colorScheme.error)
                    Text("聊天历史", style = MaterialTheme.typography.titleMedium)
                    messages.forEach { message ->
                        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) { Column(Modifier.padding(12.dp)) {
                            Text("[${message.role}] ${message.content}")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { lifecycleScope.launch {
                                    val next = when (message.role) { "SELF" -> "OTHER"; "OTHER" -> "UNKNOWN"; else -> "SELF" }
                                    dao.updateMessage(message.copy(role = next)); reload()
                                } }) { Text("修正角色") }
                                TextButton(onClick = { lifecycleScope.launch { dao.deleteMessage(message.id); reload() } }) { Text("删除") }
                            }
                        } }
                    }
                }
            }
            if (showDeleteDialog) AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("删除会话？") },
                text = { Text("该会话的聊天历史和个性化配置将被永久删除。") },
                confirmButton = { TextButton(onClick = {
                    showDeleteDialog = false
                    lifecycleScope.launch { dao.deleteConversation(id); finish() }
                }) { Text("确认删除", color = MaterialTheme.colorScheme.error) } },
                dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("取消") } },
                shape = RoundedCornerShape(24.dp)
            )
        } }
    }
}
