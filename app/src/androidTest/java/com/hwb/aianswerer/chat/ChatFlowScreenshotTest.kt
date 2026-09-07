package com.hwb.aianswerer.chat

import android.content.Intent
import android.graphics.Bitmap
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hwb.aianswerer.chat.model.*
import com.hwb.aianswerer.chat.storage.*
import com.hwb.aianswerer.chat.ui.ChatCompanionActivity
import com.hwb.aianswerer.chat.ui.ConversationDetailActivity
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Synthetic data + local mock transport only. Run with -PchatmateDebugSuffix=.preview. */
@RunWith(AndroidJUnit4::class)
class ChatFlowScreenshotTest {
    @Test fun previewHistoryAndCopyReturnFlow() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".preview")) { "Use an isolated .preview build for screenshot QA" }
        val dao = ChatDatabase.get(context).chatDao()
        val server = MockWebServer()
        server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(
            "data: {\"choices\":[{\"delta\":{\"content\":\"今天这班上得够费电的，晚上歇会儿\"}}]}\n\ndata: [DONE]\n\n"))
        server.start()
        val app = dao.getOrCreateApp("chatmate.qa.fixture", "微信 · 示例")
        val contact = dao.getOrCreateConversation(app.id, "preview-contact", "小林", "PRIVATE")
        val texts = listOf(MessageRole.OTHER to "今天忙不忙呀", MessageRole.SELF to "刚忙完，准备找点吃的",
            MessageRole.OTHER to "我今天开了一下午会", MessageRole.OTHER to "今天开会开麻了")
        val messages = texts.mapIndexed { index, (role, content) ->
            ChatMessage(conversationId = contact.id, role = role, content = content,
                timestamp = System.currentTimeMillis() - (4 - index) * 60_000, source = NodeSource.ACCESSIBILITY)
        }
        dao.insertMessages(messages.map { MessageEntity(conversationId = contact.id, role = it.role.name,
            content = it.content, timestamp = it.timestamp, source = it.source.name) })
        dao.saveAIConfig(ConversationAIConfigEntity(contact.id, inheritApiBaseUrl = false, apiBaseUrl = server.url("/v1").toString(),
            inheritApiKey = false, apiKey = "local-fixture", inheritModel = false, model = "fixture"))
        fun screenshot(name: String) {
            instrumentation.waitForIdleSync()
            Thread.sleep(800)
            val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            File(context.getExternalFilesDir(null), "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            fun shell(command: String) {
                android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
                    .use { it.readBytes() }
            }
            shell("mkdir -p /sdcard/Download/chatmate-qa")
            shell("cp ${context.getExternalFilesDir(null)}/$name.png /sdcard/Download/chatmate-qa/$name.png")
        }
        fun findText(node: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
            if (node == null) return null
            if (node.text?.toString()?.contains(text) == true && node.isEnabled) return node
            for (index in 0 until node.childCount) {
                findText(node.getChild(index), text)?.let { return it }
            }
            return null
        }
        try {
            context.startActivity(Intent(context, ConversationDetailActivity::class.java).putExtra("conversation_id", contact.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            Thread.sleep(1800)
            // Android 17 can show an existing native-library page-size compatibility dialog.
            repeat(10) {
                findText(instrumentation.uiAutomation.rootInActiveWindow, "OK")?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Thread.sleep(200)
            }
            screenshot("history")
            context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            Thread.sleep(800)
            ChatSession.active = ActiveChatSession(contact.id, ParsedChatScreen(
                ResolvedConversation(app.packageName, "微信", contact.conversationKey, contact.displayName, ConversationType.PRIVATE),
                messages, emptyList(), ChatPageDetection(true, .9f, ""), 1080, 2400), messages)
            context.startActivity(Intent(context, ChatCompanionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            val deadline = System.currentTimeMillis() + 15_000
            var button: AccessibilityNodeInfo? = null
            while (System.currentTimeMillis() < deadline) {
                val root = instrumentation.uiAutomation.rootInActiveWindow
                findText(root, "OK")?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                button = findText(root, "复制并返回聊天")
                if (button != null && server.requestCount > 0) {
                    Thread.sleep(500)
                    break
                }
                Thread.sleep(200)
            }
            screenshot("reply")
            assertNotNull("Copy-return button is visible", button)
            val reply = findText(instrumentation.uiAutomation.rootInActiveWindow, "今天这班上得够费电的")
            assertNotNull("Streamed draft is visible", reply)
            reply?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            Thread.sleep(700)
            screenshot("reply-keyboard")
            android.os.ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation.uiAutomation.executeShellCommand("input keyevent 4")
            ).use { it.readBytes() }
            Thread.sleep(500)
            var target = findText(instrumentation.uiAutomation.rootInActiveWindow, "复制并返回聊天")
            while (target != null && !target.isClickable) target = target.parent
            assertTrue("Copy-return is actionable", target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)
            Thread.sleep(1000)
            assertEquals("Returns to the previous chat app task", "com.android.settings",
                instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString())
        } finally {
            server.shutdown()
            dao.deleteApp(app.id)
            ChatSession.active = null
        }
    }
}
