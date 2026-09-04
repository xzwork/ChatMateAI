package com.hwb.aianswerer.chat.ai

import com.hwb.aianswerer.chat.model.ChatMessage
import com.hwb.aianswerer.chat.model.MessageRole
import com.hwb.aianswerer.chat.model.ResolvedConversation

object PromptBuilder {
    data class RequestMessage(val role: String, val content: String)

    fun build(
        systemPrompt: String,
        conversation: ResolvedConversation,
        messages: List<ChatMessage>,
        purpose: String
    ): List<RequestMessage> {
        val rawConversationMessages = messages.asSequence()
            .filter { it.content.isNotBlank() }
            .filter { it.role == MessageRole.SELF || it.role == MessageRole.OTHER }
            .toList()
            .takeLast(40)
            .map {
                RequestMessage(
                    role = if (it.role == MessageRole.SELF) "assistant" else "user",
                    content = it.content.trim()
                )
            }
        // Several OpenAI-compatible providers (notably Qwen-compatible gateways)
        // reject histories that start with assistant or contain adjacent equal roles.
        val conversationMessages = mutableListOf<RequestMessage>()
        rawConversationMessages.dropWhile { it.role == "assistant" }.forEach { message ->
            val previous = conversationMessages.lastOrNull()
            if (previous?.role == message.role) {
                conversationMessages[conversationMessages.lastIndex] = previous.copy(
                    content = previous.content + "\n" + message.content
                )
            } else {
                conversationMessages += message
            }
        }

        val contextPrompt = buildString {
            if (systemPrompt.isNotBlank()) {
                appendLine(systemPrompt.trim())
                appendLine()
            }
            appendLine("你正在为一段真实聊天生成下一条回复。")
            appendLine("当前 App：${conversation.appName} (${conversation.packageName})")
            appendLine("当前聊天对象：${conversation.displayName}")
            appendLine("对话中 user 代表对方，assistant 代表我。")
            append("优先回应对方最新一条有效消息，并结合此前上下文保持连贯。")
        }
        val instruction = buildString {
            if (purpose.isNotBlank()) appendLine("本次生成意图：${purpose.trim()}")
            append("请以“我”的身份，只输出一条自然、可直接发送的回复，不要解释过程。")
        }

        if (conversationMessages.lastOrNull()?.role == "user") {
            val last = conversationMessages.last()
            conversationMessages[conversationMessages.lastIndex] = last.copy(
                content = last.content + "\n\n【回复要求】\n" + instruction
            )
        } else {
            conversationMessages += RequestMessage("user", instruction)
        }

        return buildList {
            add(RequestMessage("system", contextPrompt))
            addAll(conversationMessages)
        }
    }
}
