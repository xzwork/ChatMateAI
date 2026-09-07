package com.hwb.aianswerer.chat.parser

import android.graphics.Rect
import com.hwb.aianswerer.chat.model.*
import com.hwb.aianswerer.chat.profile.AppProfileManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = android.app.Application::class)
class ChatParserTest {
    private val pkg = "com.tencent.mm"
    private fun node(text: String, left: Int, top: Int, right: Int, bottom: Int, editable: Boolean = false,
                     source: NodeSource = NodeSource.ACCESSIBILITY) =
        ScreenNode(text, Rect(left, top, right, bottom), pkg, source = source, isEditable = editable)
    private fun parse(nodes: List<ScreenNode>, target: String = pkg) = GenericChatParser().parse(
        target, target, nodes.map { it.copy(packageName = target) }, 1000, 2000, AppProfileManager.forPackage(target, target))
    private val header get() = node("小林", 420, 90, 580, 140)
    private val composer get() = node("", 100, 1600, 800, 1670, editable = true)

    @Test fun `one incoming message with composer is a conversation in each primary app`() {
        listOf(pkg, "com.xingin.xhs", "com.ss.android.ugc.aweme", "org.telegram.messenger", "example.chat").forEach { target ->
            val result = parse(listOf(header, node("今天开会开麻了", 120, 600, 540, 650), composer), target)
            assertTrue(target, result.pageDetection.isLikelyChat)
            assertEquals("小林", result.conversation.displayName)
            assertEquals(MessageRole.OTHER, result.messages.single().role)
        }
    }
    @Test fun `keyboard and unsent draft never become messages`() {
        val result = parse(listOf(header, node("晚上再聊", 140, 600, 490, 650),
            node("这是没发送的草稿", 100, 1000, 800, 1070, editable = true), node("键盘联想词", 80, 1200, 650, 1260)))
        assertEquals(listOf("晚上再聊"), result.messages.map { it.content })
    }
    @Test fun `accessibility bubbles from same speaker stay separate even when adjacent`() {
        val result = parse(listOf(header, node("第一条", 140, 600, 400, 650), node("第二条", 140, 655, 400, 705), composer))
        assertEquals(2, result.messages.size)
    }
    @Test fun `ocr tightly wrapped lines form a single message`() {
        val result = parse(listOf(header, node("这是一条比较长的", 140, 600, 600, 650, source = NodeSource.OCR),
            node("聊天消息", 140, 662, 380, 712, source = NodeSource.OCR), composer))
        assertEquals("这是一条比较长的\n聊天消息", result.messages.single().content)
    }
    @Test fun `long outgoing bubble uses outer edge instead of text centre`() {
        assertEquals(MessageRole.SELF, RoleClassifier().classify(Rect(190, 400, 890, 500), 1000))
        assertEquals(MessageRole.OTHER, RoleClassifier().classify(Rect(110, 400, 810, 500), 1000))
        assertEquals(MessageRole.UNKNOWN, RoleClassifier().classify(Rect(410, 400, 590, 500), 1000))
    }
    @Test fun `timestamp presence and follow controls are not dialogue`() {
        val result = parse(listOf(header, node("互相关注", 440, 180, 580, 220),
            node("昨天 20:30", 440, 320, 570, 360), node("想吃火锅", 120, 600, 500, 650),
            node("已读", 820, 720, 880, 760), composer), "com.xingin.xhs")
        assertEquals(listOf("想吃火锅"), result.messages.map { it.content })
    }
    @Test fun `feed navigation rejects misleading message layout`() {
        val result = parse(listOf(header, node("一条笔记", 120, 600, 500, 650), composer,
            node("首页", 80, 1850, 180, 1900), node("消息", 700, 1850, 800, 1900)), "com.xingin.xhs")
        assertFalse(result.pageDetection.isLikelyChat)
    }
    @Test fun `comment composer is not a private conversation`() {
        val result = parse(listOf(node("全部评论", 400, 100, 600, 150), node("好好看", 120, 600, 400, 650), composer))
        assertFalse(result.pageDetection.isLikelyChat)
    }
    @Test fun `plain settings list without a composer is rejected`() {
        val result = parse(listOf(header, node("账号管理", 100, 400, 450, 450), node("隐私设置", 100, 600, 450, 650)), "example.app")
        assertFalse(result.pageDetection.isLikelyChat)
    }
    @Test fun `duplicate parent text does not repeat the message`() {
        val result = parse(listOf(header, node("同一条", 100, 590, 550, 680), node("同一条", 140, 610, 500, 660), composer))
        assertEquals(1, result.messages.size)
    }
    @Test fun `avatar resolves ambiguous wide bubble`() {
        val result = parse(listOf(header, node("头像", 910, 600, 980, 670).copy(text = "", contentDescription = "我的头像"),
            node("长消息", 120, 600, 880, 670), composer))
        assertEquals(MessageRole.SELF, result.messages.single().role)
    }
}
