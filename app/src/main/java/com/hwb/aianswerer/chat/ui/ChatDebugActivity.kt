@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.hwb.aianswerer.chat.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hwb.aianswerer.BaseActivity
import com.hwb.aianswerer.chat.ChatSession

class ChatDebugActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val session = ChatSession.active ?: run { finish(); return }
        setContent { MaterialTheme { Scaffold(topBar = { TopAppBar(title = { Text("聊天识别调试") }) }) { padding ->
            Column(Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("packageName: ${session.screen.conversation.packageName}")
                Text("ConversationResolver: ${session.screen.conversation.displayName}")
                Text("ChatPageDetector: ${session.screen.pageDetection.confidence} · ${session.screen.pageDetection.reason}")
                HorizontalDivider()
                Text("Screen Nodes", style = MaterialTheme.typography.titleMedium)
                session.screen.nodes.forEach { node -> Text("[${node.source}] ${node.bounds.flattenToString()}\n${node.text}") }
                HorizontalDivider()
                Text("MessageClusterer / RoleClassifier", style = MaterialTheme.typography.titleMedium)
                session.screen.messages.forEach { message -> Text("[${message.role}] ${message.bounds?.flattenToString().orEmpty()}\n${message.content}") }
            }
        } } }
    }
}
