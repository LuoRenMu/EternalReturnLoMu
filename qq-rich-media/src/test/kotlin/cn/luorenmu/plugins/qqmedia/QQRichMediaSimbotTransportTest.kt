package cn.luorenmu.plugins.qqmedia

import cn.luorenmu.exception.MessageReplyException
import io.ktor.http.HttpMethod
import io.ktor.http.content.TextContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.*

class QQRichMediaSimbotTransportTest {
    private val path = "/v2/groups/${"A".repeat(32)}/upload_prepare"

    @Test fun `QQ requests are executed as Simbot APIs with their original JSON body`() = runBlocking<Unit> {
        val body = buildJsonObject { put("file_size", "123"); put("file_type", 1) }
        val expected = buildJsonObject { put("upload_id", "fixture") }
        var calls = 0
        val transport = QQRichMediaSimbotTransport { api ->
            calls++
            assertEquals(HttpMethod.Post, api.method)
            assertEquals(path, api.url.encodedPath)
            assertEquals(body, Json.parseToJsonElement(assertIs<TextContent>(api.body).text))
            expected
        }
        assertSame(expected, transport.post(path, body))
        assertEquals(1, calls)
    }

    @Test fun `QQ error codes cannot be mistaken for successful uploads or expose response bodies`() = runBlocking<Unit> {
        val transport = QQRichMediaSimbotTransport {
            buildJsonObject { put("err_code", 40054005); put("message", "private-upload-signature") }
        }
        val error = assertFailsWith<MessageReplyException> { transport.post(path, buildJsonObject {}) }
        assertTrue(error.message.orEmpty().contains("40054005"))
        assertFalse(error.message.orEmpty().contains("private-upload-signature"))
    }

    @Test fun `SDK failure is sanitized but cancellation propagates`() = runBlocking<Unit> {
        val transport = QQRichMediaSimbotTransport { error("private-sdk-response-token") }
        val error = assertFailsWith<MessageReplyException> { transport.post(path, buildJsonObject {}) }
        assertTrue(error.message.orEmpty().contains("Simbot"))
        assertFalse(error.message.orEmpty().contains("private-sdk-response-token"))
        val cancellation = CancellationException("cancelled")
        val cancelled = QQRichMediaSimbotTransport { throw cancellation }
        assertSame(cancellation, assertFailsWith<CancellationException> { cancelled.post(path, buildJsonObject {}) })
    }

    @Test fun `storage and non-upload endpoints cannot be executed with bot authentication`() = runBlocking<Unit> {
        val transport = QQRichMediaSimbotTransport { fail("SDK must not be called") }
        for (target in listOf("https://cos.example/upload", "/app/getAppAccessToken", path.replace("upload_prepare", "messages"))) {
            assertFailsWith<IllegalArgumentException> { transport.post(target, buildJsonObject {}) }
        }
    }
}
