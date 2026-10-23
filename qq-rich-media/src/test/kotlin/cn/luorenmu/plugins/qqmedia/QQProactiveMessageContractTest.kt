package cn.luorenmu.plugins.qqmedia

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import love.forte.simbot.component.qguild.bot.QGBot
import love.forte.simbot.qguild.api.message.GroupAndC2CSendBody
import love.forte.simbot.qguild.api.message.group.GroupMessageSendApi
import love.forte.simbot.qguild.api.message.group.GroupMessageSendResult
import java.lang.reflect.Proxy
import kotlin.test.*

/** Offline contract probe: proves the installed SDK can build the documented active group payload. */
class QQProactiveMessageContractTest {
    @Test fun `installed SDK sends a group message without passive reply identifiers`() = runBlocking {
        val group = "A".repeat(32)
        var sent = false
        val bot = Proxy.newProxyInstance(QGBot::class.java.classLoader, arrayOf(QGBot::class.java)) { _, method, args ->
            check(method.name == "executeData") { "Unexpected SDK access" }
            val request = args!![0] as GroupMessageSendApi
            assertEquals("/v2/groups/$group/messages", request.url.encodedPath)
            val wire = Json.parseToJsonElement(Json.encodeToString(request.body as GroupAndC2CSendBody)).jsonObject
            assertEquals("主动消息测试", wire["content"]?.jsonPrimitive?.content)
            assertNull(wire["msg_id"])
            assertNull(wire["event_id"])
            assertNull(wire["is_wakeup"])
            sent = true
            GroupMessageSendResult("fixture", "2026-10-22T00:00:00Z")
        } as QGBot
        val body = GroupAndC2CSendBody.create("主动消息测试", 0)
        bot.executeData(GroupMessageSendApi.create(group, body))
        assertTrue(sent)
    }
}
