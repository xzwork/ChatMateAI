package com.hwb.aianswerer.chat.profile

data class AppProfile(
    val packageName: String,
    val displayName: String,
    val ignoredTexts: Set<String> = emptySet(),
    val inputHints: Set<String> = emptySet(),
    val navigationTexts: Set<String> = emptySet()
)

object AppProfileManager {
    private val profiles = listOf(
        AppProfile("com.tencent.mm", "微信",
            setOf("微信", "聊天信息", "语音通话", "视频通话"),
            setOf("发送", "按住 说话", "按住说话", "切换到键盘", "切换到按住说话"),
            setOf("通讯录", "发现", "我")),
        AppProfile("com.xingin.xhs", "小红书",
            setOf("小红书", "关注", "回关", "已关注", "互相关注", "在线", "发起聊天", "聊天设置"),
            setOf("发消息", "发送", "说点什么", "说点什么…", "说点什么...", "发送消息"),
            setOf("首页", "购物", "消息", "我")),
        AppProfile("com.ss.android.ugc.aweme", "抖音",
            setOf("抖音", "关注", "回关", "已关注", "互相关注", "在线", "聊天设置", "连续聊天天数"),
            setOf("发送消息", "发送", "发消息", "有爱评论，说点儿好听的~"),
            setOf("首页", "朋友", "消息", "我")),
        AppProfile("com.tencent.mobileqq", "QQ", setOf("QQ", "在线"), setOf("发送"), setOf("联系人", "动态")),
        AppProfile("org.telegram.messenger", "Telegram", setOf("Telegram", "online"), setOf("Message", "Send")),
        AppProfile("com.whatsapp", "WhatsApp", setOf("online", "WhatsApp"), setOf("Message", "Type a message")),
        AppProfile("com.android.mms", "短信", inputHints = setOf("短信", "发送")),
        AppProfile("com.google.android.apps.messaging", "短信", inputHints = setOf("Text message", "Send"))
    ).associateBy { it.packageName }

    fun forPackage(packageName: String, fallbackName: String): AppProfile =
        profiles[packageName] ?: AppProfile(packageName, fallbackName,
            inputHints = setOf("发送", "Send", "Message", "发送消息"))
}
