package com.hwb.aianswerer.chat.storage

import androidx.room.*

@Entity(tableName = "chat_apps", indices = [Index(value = ["packageName"], unique = true)])
data class AppEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val appName: String
)

@Entity(
    tableName = "conversations",
    foreignKeys = [ForeignKey(entity = AppEntity::class, parentColumns = ["id"], childColumns = ["appId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("appId"), Index(value = ["appId", "conversationKey"], unique = true)]
)
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val appId: Long,
    val conversationKey: String,
    val displayName: String,
    val conversationType: String,
    val createdAt: Long = System.currentTimeMillis(),
    val lastSeenAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "chat_messages",
    foreignKeys = [ForeignKey(entity = ConversationEntity::class, parentColumns = ["id"], childColumns = ["conversationId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("conversationId")]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long,
    val role: String,
    val content: String,
    val timestamp: Long,
    val source: String
)

@Entity(
    tableName = "conversation_ai_configs",
    foreignKeys = [ForeignKey(entity = ConversationEntity::class, parentColumns = ["id"], childColumns = ["conversationId"], onDelete = ForeignKey.CASCADE)]
)
data class ConversationAIConfigEntity(
    @PrimaryKey val conversationId: Long,
    val inheritApiBaseUrl: Boolean = true,
    val apiBaseUrl: String = "",
    val inheritApiKey: Boolean = true,
    val apiKey: String = "",
    val inheritModel: Boolean = true,
    val model: String = "",
    val inheritSystemPrompt: Boolean = true,
    val systemPrompt: String = "",
    val inheritTemperature: Boolean = true,
    val temperature: Double = 0.7,
    val inheritMaxTokens: Boolean = true,
    val maxTokens: Int = 1024,
    val persistPurpose: Boolean = false,
    val purpose: String = ""
)

data class ConversationSummary(
    val id: Long,
    val appId: Long,
    val packageName: String,
    val appName: String,
    val displayName: String,
    val conversationType: String,
    val lastSeenAt: Long,
    val lastMessage: String,
    val messageCount: Int
)

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_apps WHERE packageName = :packageName LIMIT 1")
    suspend fun findApp(packageName: String): AppEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertApp(app: AppEntity): Long

    @Query("SELECT * FROM conversations WHERE appId = :appId AND conversationKey = :key LIMIT 1")
    suspend fun findConversation(appId: Long, key: String): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE appId = :appId ORDER BY lastSeenAt DESC")
    suspend fun conversationsForApp(appId: Long): List<ConversationEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertConversation(conversation: ConversationEntity): Long

    @Update suspend fun updateConversation(conversation: ConversationEntity)

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    suspend fun conversation(id: Long): ConversationEntity?

    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY id DESC LIMIT :limit")
    suspend fun recentMessagesDescending(conversationId: Long, limit: Int): List<MessageEntity>

    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY id ASC")
    suspend fun messages(conversationId: Long): List<MessageEntity>

    @Insert suspend fun insertMessages(messages: List<MessageEntity>)
    @Update suspend fun updateMessage(message: MessageEntity)
    @Query("DELETE FROM chat_messages WHERE id = :id") suspend fun deleteMessage(id: Long)

    @Query("SELECT * FROM conversation_ai_configs WHERE conversationId = :conversationId LIMIT 1")
    suspend fun aiConfig(conversationId: Long): ConversationAIConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAIConfig(config: ConversationAIConfigEntity)

    @Query("""
        SELECT conversations.id, chat_apps.id AS appId, chat_apps.packageName, chat_apps.appName,
               conversations.displayName, conversations.conversationType, conversations.lastSeenAt,
               COALESCE((SELECT content FROM chat_messages
                         WHERE conversationId = conversations.id
                         ORDER BY id DESC LIMIT 1), '') AS lastMessage,
               (SELECT COUNT(*) FROM chat_messages
                WHERE conversationId = conversations.id) AS messageCount
        FROM conversations JOIN chat_apps ON conversations.appId = chat_apps.id
        ORDER BY conversations.lastSeenAt DESC
    """)
    suspend fun conversationSummaries(): List<ConversationSummary>

    @Query("DELETE FROM conversations WHERE id = :id") suspend fun deleteConversation(id: Long)
    @Query("DELETE FROM chat_apps WHERE id = :id") suspend fun deleteApp(id: Long)
    @Query("UPDATE conversations SET displayName = :name WHERE id = :id")
    suspend fun renameConversation(id: Long, name: String)

    @Query("UPDATE chat_messages SET conversationId = :targetId WHERE conversationId = :sourceId")
    suspend fun moveMessages(sourceId: Long, targetId: Long)

    @Transaction
    suspend fun mergeConversations(sourceId: Long, targetId: Long) {
        require(sourceId != targetId)
        val source = requireNotNull(conversation(sourceId))
        val target = requireNotNull(conversation(targetId))
        require(source.appId == target.appId) { "只能合并同一 App 的联系人" }
        moveMessages(sourceId, targetId)
        val sourceConfig = aiConfig(sourceId)
        if (aiConfig(targetId) == null && sourceConfig != null) saveAIConfig(sourceConfig.copy(conversationId = targetId))
        deleteConversation(sourceId)
    }

    @Transaction
    suspend fun getOrCreateApp(packageName: String, appName: String): AppEntity {
        findApp(packageName)?.let { return it }
        val id = insertApp(AppEntity(packageName = packageName, appName = appName))
        return findApp(packageName) ?: AppEntity(id, packageName, appName)
    }

    @Transaction
    suspend fun getOrCreateConversation(appId: Long, key: String, name: String, type: String): ConversationEntity {
        findConversation(appId, key)?.let {
            val updated = it.copy(lastSeenAt = System.currentTimeMillis())
            updateConversation(updated)
            return updated
        }
        val entity = ConversationEntity(appId = appId, conversationKey = key, displayName = name, conversationType = type)
        val id = insertConversation(entity)
        return findConversation(appId, key) ?: entity.copy(id = id)
    }
}
