package com.hwb.aianswerer.chat.context

import com.hwb.aianswerer.chat.model.ChatMessage
import com.hwb.aianswerer.chat.model.ParsedChatScreen
import com.hwb.aianswerer.chat.storage.ChatDao
import com.hwb.aianswerer.chat.storage.ConversationEntity
import com.hwb.aianswerer.chat.storage.MessageEntity

class ConversationContextManager(private val dao: ChatDao) {
    suspend fun merge(screen: ParsedChatScreen): Pair<Long, List<ChatMessage>> {
        val resolved = screen.conversation
        val app = dao.getOrCreateApp(resolved.packageName, resolved.appName)
        val key = ConversationKeyNormalizer.normalize(resolved.conversationKey)
        val conversation = resolveExisting(app.id, key, resolved.displayName, screen.messages)
            ?: dao.getOrCreateConversation(app.id, key, resolved.displayName, resolved.type.name)
        if (conversation.displayName != resolved.displayName && !ConversationKeyNormalizer.isGeneric(resolved.displayName)) {
            dao.updateConversation(conversation.copy(displayName = resolved.displayName, lastSeenAt = System.currentTimeMillis()))
        } else {
            dao.updateConversation(conversation.copy(lastSeenAt = System.currentTimeMillis()))
        }
        val old = dao.recentMessagesDescending(conversation.id, 80).reversed().map {
            ChatMessage(it.id, it.conversationId, enumValueOf(it.role), it.content, it.timestamp, enumValueOf(it.source))
        }
        val incoming = screen.messages.map { it.copy(conversationId = conversation.id) }
        val fresh = MessageSequenceMatcher.newSuffix(old, incoming)
        if (fresh.isNotEmpty()) dao.insertMessages(fresh.map {
            MessageEntity(conversationId = conversation.id, role = it.role.name, content = it.content, timestamp = it.timestamp, source = it.source.name)
        })
        val merged = dao.recentMessagesDescending(conversation.id, 50).reversed().map {
            ChatMessage(it.id, it.conversationId, enumValueOf(it.role), it.content, it.timestamp, enumValueOf(it.source))
        }
        return conversation.id to merged
    }

    private suspend fun resolveExisting(
        appId: Long,
        key: String,
        displayName: String,
        incoming: List<ChatMessage>
    ): ConversationEntity? {
        val candidates = dao.conversationsForApp(appId)
        val displayKey = ConversationKeyNormalizer.normalize(displayName)

        val exactMatches = if (ConversationKeyNormalizer.isGeneric(displayName)) emptyList() else candidates.filter {
            ConversationKeyNormalizer.normalize(it.conversationKey) == key ||
                ConversationKeyNormalizer.normalize(it.displayName) == displayKey
        }
        if (exactMatches.isNotEmpty()) {
            val canonical = exactMatches.maxBy { it.lastSeenAt }
            exactMatches.filter { it.id != canonical.id }.forEach { duplicate ->
                dao.mergeConversations(duplicate.id, canonical.id)
            }
            return canonical
        }

        candidates.firstOrNull {
            ConversationKeyNormalizer.isSame(ConversationKeyNormalizer.normalize(it.conversationKey), key) ||
                ConversationKeyNormalizer.isSame(ConversationKeyNormalizer.normalize(it.displayName), displayKey)
        }?.let { return it }

        val best = candidates.map { candidate ->
            val old = dao.recentMessagesDescending(candidate.id, 30).reversed().map {
                ChatMessage(it.id, it.conversationId, enumValueOf(it.role), it.content, it.timestamp, enumValueOf(it.source))
            }
            val overlap = overlapSize(old, incoming)
            val boundaryLength = if (overlap == 1 && old.lastOrNull()?.let(::signature) == incoming.firstOrNull()?.let(::signature)) {
                incoming.firstOrNull()?.content?.length ?: 0
            } else 0
            Triple(candidate, overlap, boundaryLength)
        }.maxByOrNull { it.second }
        return best?.takeIf { (_, overlap, boundaryLength) -> overlap >= 2 || (overlap == 1 && boundaryLength >= 12) }?.first
    }

    private fun overlapSize(existing: List<ChatMessage>, incoming: List<ChatMessage>): Int {
        val old = existing.map(::signature)
        val fresh = incoming.map(::signature)
        return (minOf(old.size, fresh.size) downTo 1).firstOrNull { size ->
            old.takeLast(size) == fresh.take(size)
        } ?: 0
    }

    private fun signature(message: ChatMessage) = message.content.trim().replace(Regex("\\s+"), " ")
}

object ConversationKeyNormalizer {
    private val trailingCount = Regex("[（(]\\s*\\d+\\s*[)）]\\s*$")
    private val presence = Regex("(?i)(在线|正在输入[.…]*|typing[.…]*|online)\\s*$")
    private val separators = Regex("[\\p{P}\\p{S}\\s]+")

    fun normalize(value: String): String {
        val cleaned = value.trim().lowercase()
            .replace(presence, "")
            .replace(trailingCount, "")
            .replace(separators, "")
        return cleaned.ifBlank { "unnamed" }
    }

    fun isGeneric(value: String): Boolean = normalize(value) in setOf("unnamed", "未命名会话", "未知会话")

    fun isSame(first: String, second: String): Boolean {
        if (first == second) return true
        if (first.length < 4 || second.length < 4 || kotlin.math.abs(first.length - second.length) > 1) return false
        var differences = 0
        var left = 0
        var right = 0
        while (left < first.length && right < second.length) {
            if (first[left] == second[right]) { left++; right++; continue }
            if (++differences > 1) return false
            when {
                first.length > second.length -> left++
                second.length > first.length -> right++
                else -> { left++; right++ }
            }
        }
        return differences + (first.length - left) + (second.length - right) <= 1
    }
}
