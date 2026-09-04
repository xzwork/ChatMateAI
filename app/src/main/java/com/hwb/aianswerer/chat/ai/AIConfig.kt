package com.hwb.aianswerer.chat.ai

import android.content.Context
import com.hwb.aianswerer.config.AppConfig
import com.hwb.aianswerer.chat.storage.ConversationAIConfigEntity

data class GlobalAIConfig(
    val apiBaseUrl: String,
    val apiKey: String,
    val model: String,
    val systemPrompt: String,
    val temperature: Double,
    val maxTokens: Int
)

data class ResolvedAIConfig(
    val apiBaseUrl: String,
    val apiKey: String,
    val model: String,
    val systemPrompt: String,
    val temperature: Double,
    val maxTokens: Int
)

object GlobalAIConfigStore {
    private const val DEFAULT_PROMPT = "你是我的聊天回复助手。请根据聊天上下文，按照我的正常聊天风格生成自然回复，不要出现明显 AI 味。"
    private const val PREFS_NAME = "chat_companion_settings"
    private const val KEY_SYSTEM_PROMPT = "system_prompt"
    private const val KEY_TEMPERATURE = "temperature"
    private const val KEY_MAX_TOKENS = "max_tokens"

    fun read(context: Context): GlobalAIConfig {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return GlobalAIConfig(
            AppConfig.getApiUrl(), AppConfig.getApiKey(), AppConfig.getModelName(),
            prefs.getString(KEY_SYSTEM_PROMPT, DEFAULT_PROMPT).orEmpty().ifBlank { DEFAULT_PROMPT },
            prefs.getFloat(KEY_TEMPERATURE, 0.7f).toDouble(),
            prefs.getInt(KEY_MAX_TOKENS, 1024)
        )
    }

    fun save(context: Context, config: GlobalAIConfig) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_SYSTEM_PROMPT, config.systemPrompt.trim().ifBlank { DEFAULT_PROMPT })
            .putFloat(KEY_TEMPERATURE, config.temperature.coerceIn(0.0, 2.0).toFloat())
            .putInt(KEY_MAX_TOKENS, config.maxTokens.coerceIn(64, 32768))
            .apply()
    }
}

object AIConfigResolver {
    fun resolve(global: GlobalAIConfig, local: ConversationAIConfigEntity?): ResolvedAIConfig {
        if (local == null) return global.toResolved()
        return ResolvedAIConfig(
            if (local.inheritApiBaseUrl) global.apiBaseUrl else local.apiBaseUrl,
            if (local.inheritApiKey) global.apiKey else local.apiKey,
            if (local.inheritModel) global.model else local.model,
            if (local.inheritSystemPrompt) global.systemPrompt else local.systemPrompt,
            if (local.inheritTemperature) global.temperature else local.temperature,
            if (local.inheritMaxTokens) global.maxTokens else local.maxTokens
        )
    }

    private fun GlobalAIConfig.toResolved() = ResolvedAIConfig(apiBaseUrl, apiKey, model, systemPrompt, temperature, maxTokens)
}
