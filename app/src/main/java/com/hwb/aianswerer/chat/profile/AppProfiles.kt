package com.hwb.aianswerer.chat.profile

data class AppProfile(
    val packageName: String,
    val displayName: String,
    val ignoredTexts: Set<String> = emptySet(),
    val inputHints: Set<String> = emptySet()
)

object AppProfileManager {
    private val profiles = listOf(
        AppProfile("com.tencent.mm", "微信", setOf("微信", "聊天信息"), setOf("发送", "说点什么")),
        AppProfile("org.telegram.messenger", "Telegram", setOf("Telegram"), setOf("Message", "Send")),
        AppProfile("com.android.mms", "短信", inputHints = setOf("短信", "发送")),
        AppProfile("com.google.android.apps.messaging", "短信", inputHints = setOf("Text message", "Send")),
        AppProfile("com.xingin.xhs", "小红书", inputHints = setOf("发消息", "发送")),
        AppProfile("com.ss.android.ugc.aweme", "抖音", inputHints = setOf("发送消息", "发送"))
    ).associateBy { it.packageName }

    fun forPackage(packageName: String, fallbackName: String): AppProfile =
        profiles[packageName] ?: AppProfile(packageName, fallbackName)
}
