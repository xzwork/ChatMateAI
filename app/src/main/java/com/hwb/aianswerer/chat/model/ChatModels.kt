package com.hwb.aianswerer.chat.model

import android.graphics.Rect

enum class NodeSource { ACCESSIBILITY, OCR }
enum class MessageRole { SELF, OTHER, SYSTEM, UNKNOWN }
enum class ConversationType { PRIVATE, GROUP, UNKNOWN }

data class ScreenNode(
    val text: String,
    val bounds: Rect,
    val packageName: String,
    val className: String? = null,
    val viewId: String? = null,
    val contentDescription: String? = null,
    val source: NodeSource,
    val isEditable: Boolean = false,
    val isClickable: Boolean = false
)

data class ChatMessage(
    val id: Long = 0,
    val conversationId: Long = 0,
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val source: NodeSource,
    val bounds: Rect? = null
)

data class ResolvedConversation(
    val packageName: String,
    val appName: String,
    val conversationKey: String,
    val displayName: String,
    val type: ConversationType = ConversationType.UNKNOWN
)

data class ChatPageDetection(val isLikelyChat: Boolean, val confidence: Float, val reason: String)

data class ParsedChatScreen(
    val conversation: ResolvedConversation,
    val messages: List<ChatMessage>,
    val nodes: List<ScreenNode>,
    val pageDetection: ChatPageDetection,
    val screenWidth: Int,
    val screenHeight: Int
)
