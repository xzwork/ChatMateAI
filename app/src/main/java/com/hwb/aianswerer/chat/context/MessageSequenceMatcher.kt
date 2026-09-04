package com.hwb.aianswerer.chat.context

import com.hwb.aianswerer.chat.model.ChatMessage

object MessageSequenceMatcher {
    fun newSuffix(existing: List<ChatMessage>, incoming: List<ChatMessage>): List<ChatMessage> {
        if (existing.isEmpty()) return incoming
        val old = existing.map(::signature)
        val fresh = incoming.map(::signature)
        val maxOverlap = minOf(old.size, fresh.size)
        val overlap = (maxOverlap downTo 1).firstOrNull { size -> old.takeLast(size) == fresh.take(size) } ?: 0
        return incoming.drop(overlap)
    }

    private fun signature(message: ChatMessage): String =
        "${message.role}:${message.content.trim().replace(Regex("\\s+"), " ")}"
}
