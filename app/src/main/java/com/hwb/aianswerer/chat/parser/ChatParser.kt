package com.hwb.aianswerer.chat.parser

import android.graphics.Rect
import com.hwb.aianswerer.chat.model.*
import com.hwb.aianswerer.chat.profile.AppProfile
import kotlin.math.abs
import kotlin.math.max

class ChatPageDetector {
    fun detect(nodes: List<ScreenNode>, screenWidth: Int, screenHeight: Int): ChatPageDetection {
        if (nodes.size < 2) return ChatPageDetection(false, 0.1f, "可见文本过少")
        val messageArea = nodes.filter { it.bounds.centerY() in (screenHeight * 0.12).toInt()..(screenHeight * 0.9).toInt() }
        val left = messageArea.count { it.bounds.centerX() < screenWidth * 0.43f }
        val right = messageArea.count { it.bounds.centerX() > screenWidth * 0.57f }
        val hasHeader = nodes.any { it.bounds.centerY() < screenHeight * 0.18f && it.text.length in 1..40 }
        val distribution = left > 0 && right > 0
        val density = (messageArea.size / 8f).coerceAtMost(1f)
        val confidence = (density * 0.45f) + (if (distribution) 0.35f else 0f) + (if (hasHeader) 0.2f else 0f)
        return ChatPageDetection(confidence >= 0.5f, confidence, if (confidence >= 0.5f) "检测到聊天布局" else "页面可能不是聊天页面")
    }
}

class ConversationResolver {
    fun resolve(
        packageName: String,
        appName: String,
        nodes: List<ScreenNode>,
        screenWidth: Int,
        screenHeight: Int,
        profile: AppProfile
    ): ResolvedConversation {
        val ignored = profile.ignoredTexts + profile.inputHints + setOf("返回", "更多", "搜索", "在线", "发送")
        val title = nodes.asSequence()
            .filter { it.bounds.centerY() in (screenHeight * 0.025).toInt()..(screenHeight * 0.22).toInt() }
            .filter { node ->
                val text = node.text.trim()
                text.length in 1..40 && text !in ignored && !text.matches(Regex("\\d{1,2}:\\d{2}"))
            }
            .minByOrNull { node ->
                abs(node.bounds.centerX() - screenWidth / 2) +
                    (abs(node.bounds.centerY() - screenHeight * 0.10f) * 0.25f)
            }
            ?.text?.trim()
            ?: "未命名会话"
        val type = if (title.contains("群") || title.matches(Regex(".*\\(\\d+\\).*"))) ConversationType.GROUP else ConversationType.UNKNOWN
        return ResolvedConversation(packageName, profile.displayName.ifBlank { appName }, title, title, type)
    }
}

class RoleClassifier {
    fun classify(bounds: Rect, screenWidth: Int): MessageRole {
        val center = bounds.centerX().toFloat() / screenWidth.coerceAtLeast(1)
        val left = bounds.left.toFloat() / screenWidth.coerceAtLeast(1)
        val right = bounds.right.toFloat() / screenWidth.coerceAtLeast(1)
        return when {
            center >= 0.58f || (right >= 0.72f && left >= 0.30f) -> MessageRole.SELF
            center <= 0.42f || (left <= 0.28f && right <= 0.70f) -> MessageRole.OTHER
            else -> MessageRole.UNKNOWN
        }
    }
}

class MessageClusterer(private val classifier: RoleClassifier = RoleClassifier()) {
    private val noise = Regex("^(\\d{1,2}:\\d{2}|发送|Send|返回|更多|语音|表情|图片)$", RegexOption.IGNORE_CASE)

    fun cluster(nodes: List<ScreenNode>, conversationId: Long, screenWidth: Int, screenHeight: Int, profile: AppProfile): List<ChatMessage> {
        val candidates = nodes
            .filter { it.bounds.centerY() > screenHeight * 0.13f && it.bounds.centerY() < screenHeight * 0.88f }
            .filter { it.text.isNotBlank() && !noise.matches(it.text.trim()) && it.text.trim() !in profile.inputHints }
            .sortedWith(compareBy<ScreenNode> { it.bounds.top }.thenBy { it.bounds.left })
        val groups = mutableListOf<MutableList<ScreenNode>>()
        candidates.forEach { node ->
            val last = groups.lastOrNull()
            val lastNode = last?.lastOrNull()
            val canMerge = lastNode != null && isWrappedLine(lastNode, node, screenWidth, screenHeight)
            if (canMerge) last!!.add(node) else groups += mutableListOf(node)
        }
        return groups.mapNotNull { group ->
            val content = group.joinToString("\n") { it.text.trim() }.trim()
            if (content.isBlank()) return@mapNotNull null
            val bounds = Rect(
                group.minOf { it.bounds.left }, group.minOf { it.bounds.top },
                group.maxOf { it.bounds.right }, group.maxOf { it.bounds.bottom }
            )
            var role = group.asSequence()
                .map { classifier.classify(it.bounds, screenWidth) }
                .filter { it != MessageRole.UNKNOWN }
                .groupingBy { it }
                .eachCount()
                .maxByOrNull { it.value }
                ?.key ?: classifier.classify(bounds, screenWidth)
            if (role == MessageRole.UNKNOWN && bounds.width() > screenWidth * 0.7f) role = MessageRole.SYSTEM
            ChatMessage(conversationId = conversationId, role = role, content = content, source = group.first().source, bounds = bounds)
        }
    }

    /**
     * OCR returns each visual line as a node, while accessibility usually returns the
     * whole bubble. Merge only tightly stacked, horizontally aligned lines so two
     * consecutive bubbles from the same sender remain separate messages.
     */
    private fun isWrappedLine(previous: ScreenNode, current: ScreenNode, screenWidth: Int, screenHeight: Int): Boolean {
        if (previous.source != current.source) return false

        val previousRole = classifier.classify(previous.bounds, screenWidth)
        val currentRole = classifier.classify(current.bounds, screenWidth)
        if (previousRole != currentRole && previousRole != MessageRole.UNKNOWN && currentRole != MessageRole.UNKNOWN) return false

        val lineHeight = max(1, minOf(previous.bounds.height(), current.bounds.height()))
        val verticalGap = current.bounds.top - previous.bounds.bottom
        val maximumWrappedLineGap = max(lineHeight * 0.55f, screenHeight * 0.004f)
        if (verticalGap < -lineHeight * 0.35f || verticalGap > maximumWrappedLineGap) return false

        val edgeTolerance = max(lineHeight * 1.5f, screenWidth * 0.045f)
        val leftAligned = abs(current.bounds.left - previous.bounds.left) <= edgeTolerance
        val rightAligned = abs(current.bounds.right - previous.bounds.right) <= edgeTolerance
        val horizontalOverlap = minOf(current.bounds.right, previous.bounds.right) -
            maxOf(current.bounds.left, previous.bounds.left)
        val overlapRatio = horizontalOverlap.toFloat() /
            minOf(current.bounds.width(), previous.bounds.width()).coerceAtLeast(1)

        return (leftAligned || rightAligned) && overlapRatio >= 0.20f
    }
}

class GenericChatParser(
    private val detector: ChatPageDetector = ChatPageDetector(),
    private val resolver: ConversationResolver = ConversationResolver(),
    private val clusterer: MessageClusterer = MessageClusterer()
) {
    fun parse(packageName: String, appName: String, nodes: List<ScreenNode>, screenWidth: Int, screenHeight: Int, profile: AppProfile): ParsedChatScreen {
        val detection = detector.detect(nodes, screenWidth, screenHeight)
        val conversation = resolver.resolve(packageName, appName, nodes, screenWidth, screenHeight, profile)
        return ParsedChatScreen(conversation, clusterer.cluster(nodes, 0, screenWidth, screenHeight, profile), nodes, detection, screenWidth, screenHeight)
    }
}
