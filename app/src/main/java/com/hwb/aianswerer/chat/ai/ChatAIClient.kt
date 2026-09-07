package com.hwb.aianswerer.chat.ai

import com.google.gson.JsonObject
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.hwb.aianswerer.utils.JsonUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class ChatAIClient(private val client: OkHttpClient = defaultClient()) {

    suspend fun generate(
        config: ResolvedAIConfig,
        messages: List<PromptBuilder.RequestMessage>,
        onContent: suspend (String) -> Unit = {}
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(config.apiBaseUrl.startsWith("http")) { "API Base URL 无效" }
            require(config.apiKey.isNotBlank()) { "请先配置 API Key" }
            require(config.model.isNotBlank()) { "请先配置模型" }
            val body = JsonObject().apply {
                addProperty("model", config.model)
                addProperty("temperature", config.temperature)
                addProperty("max_tokens", config.maxTokens)
                addProperty("stream", true)
                add("messages", JsonArray().apply {
                    messages.forEach { message ->
                        add(JsonObject().apply {
                            // Build protocol field names explicitly. Release builds use
                            // R8, so serialized Kotlin property names must not be relied on.
                            addProperty("role", message.role)
                            addProperty("content", message.content)
                        })
                    }
                })
            }
            val request = Request.Builder()
                .url(endpoint(config.apiBaseUrl))
                .header("Authorization", "Bearer ${config.apiKey}")
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream, application/json")
                .header("Accept-Encoding", "identity")
                .header("Cache-Control", "no-cache")
                .post(JsonUtil.gson.toJson(body).toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            val call = client.newCall(request)
            coroutineScope {
                val cancellation = launch(start = CoroutineStart.UNDISPATCHED) {
                    try { awaitCancellation() } finally { call.cancel() }
                }
                try {
                    call.execute().use { response ->
                        if (!response.isSuccessful) error("模型服务返回 HTTP ${response.code}")
                        val responseBody = response.body ?: error("模型未返回内容")
                        readResponse(responseBody, onContent)
                    }
                } finally { cancellation.cancel() }
            }
        }.also { currentCoroutineContext().ensureActive() }
    }

    /** Supports normal OpenAI SSE and falls back to a non-stream JSON response. */
    private suspend fun readResponse(
        body: ResponseBody,
        onContent: suspend (String) -> Unit
    ): String {
        val content = StringBuilder()
        val nonStreamBody = StringBuilder()
        var sawSseData = false
        var lastDisplayed = ""
        var lastDisplayAt = 0L

        fun appendChunk(delta: String): String {
            val current = content.toString()
            if (current.isNotEmpty() && delta.length > current.length && delta.startsWith(current)) {
                content.clear()
                content.append(delta)
            } else {
                content.append(delta)
            }
            return content.toString()
        }

        body.source().use { source ->
            while (true) {
                currentCoroutineContext().ensureActive()
                val line = source.readUtf8Line() ?: break
                val normalizedLine = line.trimStart()
                if (!normalizedLine.startsWith("data:")) {
                    val ndjsonDelta = normalizedLine.takeIf { it.startsWith("{") }
                        ?.let(::extractChunkContent).orEmpty()
                    if (ndjsonDelta.isNotEmpty()) {
                        sawSseData = true
                        lastDisplayed = appendChunk(ndjsonDelta)
                        onContent(lastDisplayed)
                    } else if (!sawSseData) {
                        nonStreamBody.append(line)
                    }
                    continue
                }

                sawSseData = true
                val data = normalizedLine.removePrefix("data:").trim()
                if (data == "[DONE]") break
                val delta = extractChunkContent(data)
                if (delta.isEmpty()) continue

                appendChunk(delta)
                val now = System.currentTimeMillis()
                if (now - lastDisplayAt >= DISPLAY_INTERVAL_MS) {
                    lastDisplayed = content.toString()
                    onContent(lastDisplayed)
                    lastDisplayAt = now
                }
            }
        }

        val result = if (sawSseData) {
            content.toString().trim()
        } else {
            parseNonStreamContent(nonStreamBody.toString())
        }
        if (result.isBlank()) error("模型未返回内容")
        if (result != lastDisplayed) onContent(result)
        return result
    }

    /** Extracts visible output from common OpenAI-compatible streaming variants. */
    private fun extractChunkContent(raw: String): String = runCatching {
        val root = JsonParser.parseString(raw).asJsonObject
        root.get("delta")?.takeUnless { it.isJsonNull }?.let(::elementText)?.takeIf { it.isNotEmpty() }
            ?: root.get("output_text")?.takeUnless { it.isJsonNull }?.let(::elementText)?.takeIf { it.isNotEmpty() }
            ?: root.getAsJsonArray("choices")?.firstOrNull()?.asJsonObject?.let { choice ->
                choice.getAsJsonObject("delta")?.get("content")?.let(::elementText)
                    ?: choice.getAsJsonObject("message")?.get("content")?.let(::elementText)
                    ?: choice.get("text")?.let(::elementText)
            }.orEmpty()
    }.getOrDefault("")

    private fun elementText(element: JsonElement): String = when {
        element.isJsonNull -> ""
        element.isJsonPrimitive -> element.asString
        element.isJsonArray -> element.asJsonArray.joinToString("") { item ->
            if (item.isJsonPrimitive) item.asString
            else item.asJsonObject.get("text")?.let(::elementText).orEmpty()
        }
        element.isJsonObject -> element.asJsonObject.get("text")?.let(::elementText).orEmpty()
        else -> ""
    }

    private fun parseNonStreamContent(raw: String): String {
        val root = JsonUtil.gson.fromJson(raw, JsonObject::class.java)
        return root.getAsJsonArray("choices")?.firstOrNull()?.asJsonObject
            ?.getAsJsonObject("message")?.get("content")
            ?.takeUnless { it.isJsonNull }?.asString?.trim().orEmpty()
    }

    private fun endpoint(url: String): String {
        val value = url.trim().trimEnd('/')
        return when {
            value.endsWith("/chat/completions") -> value
            value.endsWith("/v1") -> "$value/chat/completions"
            else -> "$value/v1/chat/completions"
        }
    }

    companion object {
        private fun defaultClient() = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

        const val CONNECT_TIMEOUT_SECONDS = 30L
        const val READ_TIMEOUT_SECONDS = 300L
        const val WRITE_TIMEOUT_SECONDS = 30L
        const val CALL_TIMEOUT_SECONDS = 300L
        private const val DISPLAY_INTERVAL_MS = 32L
    }
}
