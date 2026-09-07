package com.hwb.aianswerer.chat.parser

import android.graphics.Rect
import com.hwb.aianswerer.chat.model.*
import com.hwb.aianswerer.chat.profile.AppProfile
import kotlin.math.abs
import kotlin.math.max

/** Geometry is derived from the visible composer, including when the keyboard is open. */
internal object ChatLayout {
    private val status = Regex("^(\\d{1,2}:\\d{2}|\\d{1,3}%|已读|未读|送达|已送达|Read|Seen|Delivered|在线|online|正在输入[.…]*|typing[.…]*|昨天|今天|星期[一二三四五六日天](\\s.*)?|\\d{1,2}月\\d{1,2}日(\\s.*)?|\\d{4}[/年.-]\\d{1,2}[/月.-]\\d{1,2}日?(\\s.*)?|昨天\\s+\\d{1,2}:\\d{2}|今天\\s+\\d{1,2}:\\d{2})$", RegexOption.IGNORE_CASE)
    private val controls = setOf("返回", "更多", "搜索", "表情", "语音", "切换到键盘", "添加", "聊天信息")

    fun isInput(node: ScreenNode) = node.isEditable || node.className?.contains("EditText") == true
    fun isNoise(text: String, profile: AppProfile): Boolean =
        text.isBlank() || status.matches(text.trim()) || text.trim() in controls ||
            text.trim() in profile.ignoredTexts || text.trim() in profile.inputHints

    fun composerTop(nodes: List<ScreenNode>, height: Int, profile: AppProfile): Int =
        nodes.filter {
            it.bounds.top > height * .3f && (isInput(it) ||
                it.text.trim() in profile.inputHints || it.contentDescription?.trim() in profile.inputHints)
        }.minOfOrNull { it.bounds.top } ?: (height * .9f).toInt()

    fun title(nodes: List<ScreenNode>, width: Int, height: Int, profile: AppProfile): ScreenNode? =
        nodes.filter {
            it.bounds.centerY() in (height * .035f).toInt()..(height * .16f).toInt() &&
                it.bounds.centerX() in (width * .16f).toInt()..(width * .84f).toInt() &&
                !isInput(it) && it.text.trim().length in 1..40 && !isNoise(it.text, profile)
        }.minByOrNull { abs(it.bounds.centerX() - width / 2) + abs(it.bounds.centerY() - height * .08f) }

    fun body(nodes: List<ScreenNode>, width: Int, height: Int, profile: AppProfile): List<ScreenNode> {
        val top = title(nodes, width, height, profile)?.bounds?.bottom ?: (height * .12f).toInt()
        val bottom = composerTop(nodes, height, profile)
        return nodes.filter {
            !isInput(it) && it.bounds.top > top && it.bounds.bottom <= bottom &&
                !isNoise(it.text, profile) && it.className?.endsWith("Button") != true
        }.distinctBy { listOf(it.text.trim(), it.bounds.left, it.bounds.top, it.bounds.right, it.bounds.bottom) }
            .filter { parent ->
                // Some frameworks expose the same text on a container and its child.
                nodes.none { child -> child !== parent && child.text.trim() == parent.text.trim() &&
                    child.bounds != parent.bounds && parent.bounds.contains(child.bounds) }
            }
    }
}

class ChatPageDetector {
    fun detect(nodes: List<ScreenNode>, screenWidth: Int, screenHeight: Int,
               profile: AppProfile = AppProfile("", "")): ChatPageDetection {
        val navigation = nodes.count { it.bounds.top > screenHeight * .86f && it.text.trim() in profile.navigationTexts }
        val nonChatHeader = nodes.any { it.bounds.centerY() < screenHeight * .18f &&
            it.text.trim().matches(Regex("^(评论|全部评论|搜索|搜索结果|通讯录|通知|Comments)(.*)$")) }
        if (navigation >= 2 || nonChatHeader) return ChatPageDetection(false, .1f, "请打开私信或聊天对话，暂不支持信息流和评论区")
        val body = ChatLayout.body(nodes, screenWidth, screenHeight, profile)
        val hasTitle = ChatLayout.title(nodes, screenWidth, screenHeight, profile) != null
        val hasComposer = nodes.any { it.bounds.top > screenHeight * .3f &&
            (ChatLayout.isInput(it) || it.text.trim() in profile.inputHints || it.contentDescription?.trim() in profile.inputHints) }
        val roles = body.map { RoleClassifier().classify(it.bounds, screenWidth) }.toSet()
        val hasBothSides = MessageRole.SELF in roles && MessageRole.OTHER in roles
        val likely = hasTitle && body.isNotEmpty() && (hasComposer || hasBothSides)
        return ChatPageDetection(likely, if (likely) if (hasComposer) .9f else .65f else .25f,
            if (likely) "已识别聊天区域" else "请打开具体聊天，并让联系人名称和最近消息显示在屏幕上")
    }
}

class ConversationResolver {
    fun resolve(packageName: String, appName: String, nodes: List<ScreenNode>, screenWidth: Int,
                screenHeight: Int, profile: AppProfile): ResolvedConversation {
        val title = ChatLayout.title(nodes, screenWidth, screenHeight, profile)?.text?.trim() ?: "未命名会话"
        val type = if (title.matches(Regex(".*[（(]\\d+[)）]$"))) ConversationType.GROUP else ConversationType.UNKNOWN
        return ResolvedConversation(packageName, profile.displayName.ifBlank { appName }, title, title, type)
    }
}

class RoleClassifier {
    fun classify(bounds: Rect, screenWidth: Int): MessageRole {
        val width = screenWidth.coerceAtLeast(1)
        val left = bounds.left.toFloat() / width
        val right = bounds.right.toFloat() / width
        // Long bubbles cross the centre. The outer edge is more useful than text centre.
        return when {
            left >= .3f && right >= .65f -> MessageRole.SELF
            left <= .28f && right <= .7f -> MessageRole.OTHER
            left >= .17f && right >= .82f -> MessageRole.SELF
            left <= .18f && right <= .83f -> MessageRole.OTHER
            else -> MessageRole.UNKNOWN
        }
    }
}

class MessageClusterer(private val classifier: RoleClassifier = RoleClassifier()) {
    fun cluster(nodes: List<ScreenNode>, conversationId: Long, screenWidth: Int, screenHeight: Int,
                profile: AppProfile): List<ChatMessage> {
        val candidates = ChatLayout.body(nodes, screenWidth, screenHeight, profile)
            .sortedWith(compareBy<ScreenNode> { it.bounds.top }.thenBy { it.bounds.left })
        val groups = mutableListOf<MutableList<ScreenNode>>()
        candidates.forEach { node ->
            val previous = groups.lastOrNull()?.lastOrNull()
            if (previous != null && isWrappedLine(previous, node, screenWidth, screenHeight)) groups.last().add(node)
            else groups.add(mutableListOf(node))
        }
        return groups.map { group ->
            val bounds = Rect(group.minOf { it.bounds.left }, group.minOf { it.bounds.top },
                group.maxOf { it.bounds.right }, group.maxOf { it.bounds.bottom })
            // Avatar anchors help identify long messages whose text has ambiguous alignment.
            val avatar = nodes.filter {
                it.contentDescription?.contains("头像") == true ||
                    it.contentDescription?.contains("avatar", ignoreCase = true) == true
            }.filter { abs(it.bounds.top - bounds.top) <= max(it.bounds.height(), bounds.height().coerceAtMost(screenHeight / 20)) &&
                (it.bounds.right < bounds.left || it.bounds.left > bounds.right) }
                .minByOrNull { abs(it.bounds.top - bounds.top) }
            val role = avatar?.let { classifier.classify(it.bounds, screenWidth) } ?: classifier.classify(bounds, screenWidth)
            ChatMessage(conversationId = conversationId, role = role, content = group.joinToString("\n") { it.text.trim() },
                source = group.first().source, bounds = bounds)
        }
    }

    private fun isWrappedLine(previous: ScreenNode, current: ScreenNode, screenWidth: Int, screenHeight: Int): Boolean {
        // Accessibility nodes already represent bubbles. Never join consecutive messages.
        if (previous.source != NodeSource.OCR || current.source != NodeSource.OCR) return false
        val previousRole = classifier.classify(previous.bounds, screenWidth)
        val currentRole = classifier.classify(current.bounds, screenWidth)
        if (previousRole != currentRole && previousRole != MessageRole.UNKNOWN && currentRole != MessageRole.UNKNOWN) return false
        val lineHeight = max(1, minOf(previous.bounds.height(), current.bounds.height()))
        val gap = current.bounds.top - previous.bounds.bottom
        if (gap < 0 || gap > max(lineHeight * .45f, screenHeight * .003f)) return false
        val tolerance = max(lineHeight * .75f, screenWidth * .025f)
        val aligned = abs(current.bounds.left - previous.bounds.left) <= tolerance || abs(current.bounds.right - previous.bounds.right) <= tolerance
        val overlap = minOf(current.bounds.right, previous.bounds.right) - maxOf(current.bounds.left, previous.bounds.left)
        return aligned && overlap.toFloat() / minOf(current.bounds.width(), previous.bounds.width()).coerceAtLeast(1) >= .2f
    }
}

class GenericChatParser(
    private val detector: ChatPageDetector = ChatPageDetector(),
    private val resolver: ConversationResolver = ConversationResolver(),
    private val clusterer: MessageClusterer = MessageClusterer()
) {
    fun parse(packageName: String, appName: String, nodes: List<ScreenNode>, screenWidth: Int, screenHeight: Int,
              profile: AppProfile): ParsedChatScreen {
        val ownNodes = nodes.filter { it.packageName.isBlank() || it.packageName == packageName }
        return ParsedChatScreen(resolver.resolve(packageName, appName, ownNodes, screenWidth, screenHeight, profile),
            clusterer.cluster(ownNodes, 0, screenWidth, screenHeight, profile), ownNodes,
            detector.detect(ownNodes, screenWidth, screenHeight, profile), screenWidth, screenHeight)
    }
}
