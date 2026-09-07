package com.hwb.aianswerer.chat.ai

import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class ChatAIClientTest {
    private fun config(server: MockWebServer) = ResolvedAIConfig(server.url("/v1").toString(), "test-key", "test-model", "", .7, 256)
    @Test fun `stopping generation cancels a stalled network call promptly`() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        server.start()
        try {
            val client = ChatAIClient(OkHttpClient.Builder().readTimeout(30, TimeUnit.SECONDS).build())
            val job = launch { client.generate(config(server), listOf(PromptBuilder.RequestMessage("user", "hello"))) }
            withContext(Dispatchers.IO) { assertNotNull(server.takeRequest(5, TimeUnit.SECONDS)) }
            withTimeout(3000) { job.cancelAndJoin() }
            assertTrue(job.isCancelled)
        } finally { server.shutdown() }
    }
    @Test fun `streaming response produces a single editable draft`() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(
            "data: {\"choices\":[{\"delta\":{\"content\":\"好，你\"}}]}\n\n" +
                "data: {\"choices\":[{\"delta\":{\"content\":\"先忙\"}}]}\n\ndata: [DONE]\n\n"))
        server.start()
        try {
            val updates = mutableListOf<String>()
            val result = ChatAIClient().generate(config(server), listOf(PromptBuilder.RequestMessage("user", "hello"))) { updates += it }
            assertEquals("好，你先忙", result.getOrThrow())
            assertEquals("好，你先忙", updates.last())
        } finally { server.shutdown() }
    }
}
