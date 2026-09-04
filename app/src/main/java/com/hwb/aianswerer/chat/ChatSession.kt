package com.hwb.aianswerer.chat

import com.hwb.aianswerer.chat.model.ChatMessage
import com.hwb.aianswerer.chat.model.ParsedChatScreen

data class ActiveChatSession(
    val conversationId: Long,
    val screen: ParsedChatScreen,
    val messages: List<ChatMessage>,
    val usedScreenshot: Boolean = false
)

object ChatSession {
    @Volatile var active: ActiveChatSession? = null
}
