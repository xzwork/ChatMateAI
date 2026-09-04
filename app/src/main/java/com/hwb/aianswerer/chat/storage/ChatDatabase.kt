package com.hwb.aianswerer.chat.storage

import android.content.Context
import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [AppEntity::class, ConversationEntity::class, MessageEntity::class, ConversationAIConfigEntity::class],
    version = 2,
    exportSchema = false
)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile private var instance: ChatDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE conversation_ai_configs " +
                        "ADD COLUMN persistPurpose INTEGER NOT NULL DEFAULT 0"
                )
                database.execSQL(
                    "ALTER TABLE conversation_ai_configs " +
                        "ADD COLUMN purpose TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        fun get(context: Context): ChatDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, ChatDatabase::class.java, "chat_companion.db")
                .addMigrations(MIGRATION_1_2)
                .build().also { instance = it }
        }
    }
}
