package com.hwb.aianswerer.chat.ai

enum class ReplyStyle(val label: String, val instruction: String) {
    NATURAL("自然接话", "顺着对方最新的话自然接一句，像熟悉的真人发微信。"),
    PLAYFUL("轻松有趣", "围绕眼前细节轻轻开个玩笑；对方在难过、生气或认真提问时优先认真回应，不强行抖机灵。"),
    WARM("关心一下", "先接住具体感受，再给一句轻量关心。不说教，不空泛安慰，不自作主张提供解决方案。"),
    INVITE("试着邀约", "只有存在共同兴趣、对方积极回应时，给一个轻松具体且容易拒绝的邀约。信息不足先自然接话，不编造日程或地点。"),
    DECLINE("礼貌拒绝", "清晰表达边界，语气友好简短。不含糊吊着对方，也不编造借口。")
}
