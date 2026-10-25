package cn.luorenmu.plugins.patchnotes

import cn.luorenmu.ai.AIConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import java.time.Duration
import kotlin.test.*

class PatchNotesAiClientTest {
    private val thinking = AIConfig("test-key", "Qwen/Qwen3-VL-30B-A3B-Thinking", "https://api.siliconflow.cn/v1")

    @Test fun `real completion call uses longer plugin timeout and bounded thinking without changing host config`() = runBlocking {
        var calls = 0
        val client = PatchNotesAiClient(thinking, send = { request ->
            calls++
            assertEquals(Duration.ofSeconds(300), request.timeout().orElseThrow())
            assertEquals("https://api.siliconflow.cn/v1/chat/completions", request.uri().toString())
            200 to """{"choices":[{"message":{"content":"{\"headline\":\"重点\",\"sections\":[{\"title\":\"系统\",\"points\":[\"修改\"]}]}"},"finish_reason":"stop"}]}"""
        })
        val payload = Json.parseToJsonElement(client.payload("system", "正文")).jsonObject
        assertEquals(1024, payload.getValue("thinking_budget").jsonPrimitive.int)
        assertEquals(4096, payload.getValue("max_tokens").jsonPrimitive.int)
        assertNotNull(PatchNotesSummarizer(client::complete).summarize("版本", "正文"))
        assertEquals(1, calls)
        assertEquals(thinking, AIConfig("test-key", "Qwen/Qwen3-VL-30B-A3B-Thinking", "https://api.siliconflow.cn/v1"))
    }

    @Test fun `other providers do not receive siliconflow specific thinking fields`() {
        val client = PatchNotesAiClient(thinking.copy(baseUrl = "https://example.invalid/api", model = "custom-model"))
        val payload = Json.parseToJsonElement(client.payload("system", "正文")).jsonObject
        assertFalse("thinking_budget" in payload)
        assertFalse("max_tokens" in payload)
    }

    @Test fun `failed and truncated completions are not persisted as valid analysis`() = runBlocking {
        assertNull(PatchNotesAiClient(thinking, send = { 429 to "{}" }).complete("system", "user"))
        assertNull(PatchNotesAiClient(thinking, send = {
            200 to """{"choices":[{"message":{"content":"incomplete"},"text":"truncated too","finish_reason":"length"}]}"""
        }).complete("system", "user"))
    }

    @Test fun `disabled AI does not send requests and cancellation propagates`() = runBlocking {
        assertNull(PatchNotesAiClient(thinking.copy(apiKey = ""), send = { error("must not send") }).complete("", ""))
        assertFailsWith<CancellationException> {
            PatchNotesAiClient(thinking, send = { throw CancellationException("stop") }).complete("", "")
        }
        Unit
    }
}
