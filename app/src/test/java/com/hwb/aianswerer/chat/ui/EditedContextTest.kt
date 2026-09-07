package com.hwb.aianswerer.chat.ui

import com.hwb.aianswerer.chat.model.*
import org.junit.Assert.assertEquals
import org.junit.Test

class EditedContextTest {
    private fun message(text: String) = ChatMessage(role = MessageRole.OTHER, content = text, source = NodeSource.ACCESSIBILITY)
    @Test fun `editing last screen preserves older history and replaces recognized text`() {
        val history = listOf("旧消息", "识别错误", "晚安").map(::message)
        val edited = listOf("修正内容", "晚安").map(::message)
        assertEquals(listOf("旧消息", "修正内容", "晚安"),
            mergeEditedScreenMessages(history, history.takeLast(2), edited).map { it.content })
    }
    @Test fun `editing an earlier captured page does not duplicate history or lose latest messages`() {
        val history = listOf("早上", "中午", "晚上").map(::message)
        assertEquals(listOf("早餐", "中午", "晚上"),
            mergeEditedScreenMessages(history, history.take(2), listOf("早餐", "中午").map(::message)).map { it.content })
    }
    @Test fun `partial overlap keeps complete corrected screen`() {
        assertEquals(listOf("旧", "改", "新"), mergeEditedScreenMessages(listOf("旧", "错").map(::message),
            listOf("错", "新").map(::message), listOf("改", "新").map(::message)).map { it.content })
    }
}
