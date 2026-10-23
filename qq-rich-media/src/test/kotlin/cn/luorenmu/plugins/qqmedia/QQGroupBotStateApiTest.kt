package cn.luorenmu.plugins.qqmedia

import io.ktor.http.HttpMethod
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import love.forte.simbot.component.qguild.bot.QGBot
import love.forte.simbot.qguild.ErrInfo
import love.forte.simbot.qguild.QQGuildApiException
import java.lang.reflect.Proxy
import kotlin.test.*

class QQGroupBotStateApiTest {
    private val group = "A".repeat(32)
    private val json = Json { ignoreUnknownKeys = true }
    private fun bot(result: () -> QQGroupBotState): QGBot = Proxy.newProxyInstance(
        QGBot::class.java.classLoader, arrayOf(QGBot::class.java)) { _, method, args ->
            check(method.name == "executeData")
            val request = args!![0] as QQGroupBotStateApi
            assertEquals("/v2/groups/$group/bot_state", request.url.encodedPath)
            assertEquals(HttpMethod.Get, request.method)
            result()
        } as QGBot

    @Test fun `SDK API route and serializer preserve true false and absent state`() {
        val api = QQGroupBotStateApi.create(group)
        assertEquals(HttpMethod.Get, api.method)
        assertEquals("/v2/groups/$group/bot_state", api.url.encodedPath)
        val state = json.decodeFromString(api.resultDeserializationStrategy,
            """{"member_openid":"bot","member_role":"admin","allow_proactive_msg":true,"extra":123}""")
        assertEquals(true, state.allowProactiveMsg)
        assertEquals("admin", state.memberRole)
        assertEquals(false, json.decodeFromString(api.resultDeserializationStrategy, """{"allow_proactive_msg":false}""").allowProactiveMsg)
        assertNull(json.decodeFromString(api.resultDeserializationStrategy, """{"member_role":"admin"}""").allowProactiveMsg)
        assertFailsWith<IllegalArgumentException> { QQGroupBotStateApi.create("../../other-group") }
    }

    @Test fun `query uses current Simbot and distinguishes explicit state from omitted field`() = runBlocking {
        for ((value, status) in listOf(true to QQGroupPermissionStatus.ENABLED, false to QQGroupPermissionStatus.DISABLED,
            null to QQGroupPermissionStatus.NOT_PROVIDED)) {
            val result = QQGroupPermissionQuery.query(bot { QQGroupBotState(allowProactiveMsg = value, memberRole = "owner") }, group)
            assertEquals(status, result.status)
            assertNull(result.errorCode)
        }
    }

    @Test fun `whitelist denial and HTTP failures never mean disabled or leak API descriptions`() = runBlocking {
        val denied = QQGroupPermissionQuery.query(bot {
            throw QQGuildApiException(ErrInfo(11253, "private token=secret"), 403, "secret")
        }, group)
        assertEquals(QQGroupPermissionStatus.NO_API_PERMISSION, denied.status)
        assertEquals(11253, denied.errorCode)
        assertFalse(denied.toString().contains("secret"))
        assertEquals(QQGroupPermissionStatus.NO_API_PERMISSION, QQGroupPermissionQuery.query(bot {
            throw QQGuildApiException(ErrInfo(11253, "secret"), 403, "Forbidden", "secret")
        }, group).status)
        val failed = QQGroupPermissionQuery.query(bot { throw QQGuildApiException(503, "secret") }, group)
        assertEquals(QQGroupPermissionStatus.FAILED, failed.status)
        assertEquals(503, failed.errorCode)
        assertEquals(QQGroupPermissionStatus.FAILED,
            QQGroupPermissionQuery.query(bot { error("private error") }, group).status)
    }

    @Test fun `shutdown cancellation is not swallowed as an API failure`() = runBlocking<Unit> {
        assertFailsWith<CancellationException> {
            QQGroupPermissionQuery.query(bot { throw CancellationException("shutdown") }, group)
        }
    }
}
