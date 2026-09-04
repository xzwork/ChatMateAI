@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.hwb.aianswerer.chat.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hwb.aianswerer.BaseActivity
import com.hwb.aianswerer.chat.storage.ChatDatabase
import com.hwb.aianswerer.chat.storage.ConversationSummary
import com.hwb.aianswerer.ui.theme.AIAnswererTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ChatSettingsActivity : BaseActivity() {
    private var refreshVersion by mutableIntStateOf(0)

    override fun onResume() {
        super.onResume()
        refreshVersion++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dao = ChatDatabase.get(this).chatDao()
        setContent {
            AIAnswererTheme {
                var summaries by remember { mutableStateOf(emptyList<ConversationSummary>()) }
                var deleteTarget by remember { mutableStateOf<DeleteTarget?>(null) }
                var menuConversationId by remember { mutableStateOf<Long?>(null) }
                val scope = rememberCoroutineScope()
                val pageBackground = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .38f)

                suspend fun reload() {
                    summaries = dao.conversationSummaries()
                }

                LaunchedEffect(refreshVersion) { reload() }

                Scaffold(
                    containerColor = pageBackground,
                    topBar = {
                        TopAppBar(
                            title = { Text("会话管理", fontWeight = FontWeight.SemiBold) },
                            navigationIcon = { TextButton(onClick = { finish() }) { Text("返回") } },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                    }
                ) { padding ->
                    if (summaries.isEmpty()) {
                        Box(
                            Modifier.padding(padding).fillMaxSize().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("暂无会话", style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "在聊天页面使用悬浮助手后，会话会显示在这里",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.padding(padding).fillMaxSize(),
                            contentPadding = PaddingValues(top = 10.dp, bottom = 28.dp)
                        ) {
                            item {
                                Text(
                                    "最近聊天  ·  ${summaries.size} 个会话",
                                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            itemsIndexed(summaries, key = { _, item -> item.id }) { index, item ->
                                ConversationListRow(
                                    item = item,
                                    menuExpanded = menuConversationId == item.id,
                                    onOpen = {
                                        startActivity(
                                            Intent(this@ChatSettingsActivity, ConversationDetailActivity::class.java)
                                                .putExtra("conversation_id", item.id)
                                        )
                                    },
                                    onMenu = { menuConversationId = item.id },
                                    onDismissMenu = { menuConversationId = null },
                                    onDeleteConversation = {
                                        menuConversationId = null
                                        deleteTarget = DeleteTarget.Conversation(item.id, item.displayName)
                                    },
                                    onDeleteApp = {
                                        menuConversationId = null
                                        deleteTarget = DeleteTarget.App(item.appId, item.appName)
                                    }
                                )
                                if (index != summaries.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 84.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f)
                                    )
                                }
                            }
                        }
                    }
                }

                deleteTarget?.let { target ->
                    AlertDialog(
                        onDismissRequest = { deleteTarget = null },
                        title = { Text(target.title) },
                        text = { Text(target.message) },
                        confirmButton = {
                            TextButton(onClick = {
                                deleteTarget = null
                                scope.launch {
                                    when (target) {
                                        is DeleteTarget.App -> dao.deleteApp(target.id)
                                        is DeleteTarget.Conversation -> dao.deleteConversation(target.id)
                                    }
                                    reload()
                                }
                            }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                        },
                        dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } },
                        shape = RoundedCornerShape(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ConversationListRow(
    item: ConversationSummary,
    menuExpanded: Boolean,
    onOpen: () -> Unit,
    onMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    onDeleteConversation: () -> Unit,
    onDeleteApp: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onOpen)
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(56.dp).clip(RoundedCornerShape(13.dp))
                .background(avatarColor(item.displayName)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                item.displayName.trim().take(1).ifBlank { item.appName.take(1) }.uppercase(),
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    formatConversationTime(item.lastSeenAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                buildString {
                    append(item.appName)
                    append(" · ")
                    append(if (item.lastMessage.isBlank()) "暂无消息" else item.lastMessage.replace('\n', ' '))
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box {
            TextButton(onClick = onMenu, contentPadding = PaddingValues(8.dp)) {
                Text("•••", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = onDismissMenu) {
                DropdownMenuItem(text = { Text("管理会话") }, onClick = { onDismissMenu(); onOpen() })
                DropdownMenuItem(
                    text = { Text("删除此会话", color = MaterialTheme.colorScheme.error) },
                    onClick = onDeleteConversation
                )
                DropdownMenuItem(
                    text = { Text("删除 ${item.appName} 的全部会话", color = MaterialTheme.colorScheme.error) },
                    onClick = onDeleteApp
                )
            }
        }
    }
}

private fun avatarColor(seed: String): Color {
    val palette = listOf(0xFF07C160, 0xFF5B8FF9, 0xFF7A67EE, 0xFFF59E0B, 0xFFEC4899, 0xFF14B8A6)
    return Color(palette[(seed.hashCode() and Int.MAX_VALUE) % palette.size])
}

private fun formatConversationTime(timestamp: Long): String {
    val now = Calendar.getInstance()
    val value = Calendar.getInstance().apply { timeInMillis = timestamp }
    val locale = Locale.getDefault()
    return when {
        now.get(Calendar.YEAR) == value.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == value.get(Calendar.DAY_OF_YEAR) ->
            SimpleDateFormat("HH:mm", locale).format(Date(timestamp))
        now.get(Calendar.YEAR) == value.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) - value.get(Calendar.DAY_OF_YEAR) == 1 -> "昨天"
        now.get(Calendar.YEAR) == value.get(Calendar.YEAR) ->
            SimpleDateFormat("M月d日", locale).format(Date(timestamp))
        else -> SimpleDateFormat("yyyy/M/d", locale).format(Date(timestamp))
    }
}

private sealed interface DeleteTarget {
    val title: String
    val message: String

    data class Conversation(val id: Long, val name: String) : DeleteTarget {
        override val title = "删除会话？"
        override val message = "“$name”的聊天历史和个性化配置将被永久删除。"
    }

    data class App(val id: Long, val name: String) : DeleteTarget {
        override val title = "删除 $name 的全部会话？"
        override val message = "该 App 下的聊天历史和个性化配置将被永久删除。"
    }
}
