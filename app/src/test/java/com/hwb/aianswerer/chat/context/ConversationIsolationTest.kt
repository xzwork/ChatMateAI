package com.hwb.aianswerer.chat.context

import androidx.room.Room
import com.hwb.aianswerer.chat.model.*
import com.hwb.aianswerer.chat.storage.ChatDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = android.app.Application::class)
class ConversationIsolationTest {
    private lateinit var database: ChatDatabase
    @Before fun setup() { database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), ChatDatabase::class.java)
        .allowMainThreadQueries().build() }
    @After fun close() { database.close() }
    private fun screen(name: String, pkg: String = "com.tencent.mm") = ParsedChatScreen(
        ResolvedConversation(pkg, "微信", name, name), listOf("你好", "吃饭了吗").map {
            ChatMessage(role = MessageRole.OTHER, content = it, source = NodeSource.ACCESSIBILITY)
        }, emptyList(), ChatPageDetection(true, .9f, ""), 1000, 2000)
    @Test fun `similar names with identical greetings remain separate`() = runBlocking {
        val manager = ConversationContextManager(database.chatDao())
        val first = manager.merge(screen("小林同学"))
        val second = manager.merge(screen("小李同学"))
        assertNotEquals(first.first, second.first)
        assertEquals(2, database.chatDao().conversationSummaries().size)
    }
    @Test fun `same named contact across apps remains separate`() = runBlocking {
        val manager = ConversationContextManager(database.chatDao())
        assertNotEquals(manager.merge(screen("小林")).first, manager.merge(screen("小林", "com.xingin.xhs")).first)
    }
    @Test fun `remark survives the next capture without creating another conversation`() = runBlocking {
        val dao = database.chatDao()
        val manager = ConversationContextManager(dao)
        val first = manager.merge(screen("小林"))
        dao.renameConversation(first.first, "我的备注")
        val next = manager.merge(screen("小林"))
        assertEquals(first.first, next.first)
        assertEquals("我的备注", dao.conversation(first.first)?.displayName)
    }
    @Test fun `cross app manual merge is rejected before moving records`() = runBlocking {
        val dao = database.chatDao()
        val manager = ConversationContextManager(dao)
        val first = manager.merge(screen("小林"))
        val second = manager.merge(screen("小林", "com.xingin.xhs"))
        assertTrue(runCatching { dao.mergeConversations(first.first, second.first) }.isFailure)
        assertEquals(2, dao.conversationSummaries().size)
        assertEquals(2, dao.messages(first.first).size)
    }
}
