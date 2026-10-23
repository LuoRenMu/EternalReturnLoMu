package cn.luorenmu.plugins.test

import cn.luorenmu.command.entity.MessageSender
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import love.forte.simbot.component.qguild.bot.QGBot
import love.forte.simbot.component.qguild.event.QGInteractionCreateEvent
import love.forte.simbot.component.qguild.message.QGMarkdown
import love.forte.simbot.event.EventListener
import love.forte.simbot.event.EventListenerContext
import love.forte.simbot.event.EventListenerRegistrar
import love.forte.simbot.event.EventListenerRegistrationHandle
import love.forte.simbot.event.FuzzyEventTypeImplementation
import love.forte.simbot.event.handleWith
import love.forte.simbot.message.Message
import love.forte.simbot.message.toText
import love.forte.simbot.qguild.api.message.GroupAndC2CSendBody
import love.forte.simbot.qguild.api.message.group.GroupMessageSendApi
import love.forte.simbot.qguild.api.message.group.GroupMessageSendResult
import love.forte.simbot.qguild.event.InteractionCreate
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TestButtonInteractionTest {
    private val group = "A".repeat(32)
    private val user = "B".repeat(32)
    private val outerId = "INTERACTION_CREATE:outer-event-fixture"

    private fun source(): InteractionCreate = Json { ignoreUnknownKeys = true }.decodeFromString(
        InteractionCreate.serializer(), """
        {"op":0,"s":1,"t":"INTERACTION_CREATE","id":"$outerId","d":{
          "id":"bare-interaction-fixture","type":11,"chat_type":1,"scene":"group",
          "timestamp":"2026-10-01T00:00:00Z","group_openid":"$group","group_member_openid":"$user",
          "data":{"type":11,"resolved":{"button_id":"${TestCallbackProtocol.BUTTON_ID}",
            "button_data":"${TestCallbackProtocol.DATA}"}}}}
        """.trimIndent())

    private fun interaction() = TestButtonInteraction.from(source())

    @Test fun `SDK callback mapping keeps bare acknowledgement and outer reply IDs separate`() {
        val interaction = interaction()
        assertEquals("bare-interaction-fixture", interaction.interactionId)
        assertEquals(outerId, interaction.replyEventId)
        assertEquals(group, interaction.groupId)
        assertEquals(user, interaction.userId)
        assertEquals(TestCallbackProtocol.DATA, interaction.buttonData)
    }

    @Test fun `acknowledges before querying and replies for the clicking user with the outer event ID`() = runBlocking {
        val order = mutableListOf<String>()
        val reply = "result".toText()
        var sender: MessageSender? = null
        val handler = TestButtonInteractionHandler(query = { order += "query"; sender = it; reply })
        assertTrue(handler.handle(interaction(), { order += "ack:$it" }, { destination, eventId, message ->
            order += "send"
            assertEquals(group, destination)
            assertEquals(outerId, eventId)
            assertSame(reply, message)
        }))
        assertEquals(listOf("ack:0", "query", "send"), order)
        assertEquals(group, sender?.groupOpenId.toString())
        assertEquals(user, sender?.senderOpenId.toString())
        assertEquals("/test", sender?.plainText)
        assertEquals("/test", sender?.message)
    }

    @Test fun `redelivered interaction does not acknowledge or execute twice but a new click can execute`() = runBlocking {
        var queries = 0
        val codes = mutableListOf<Int>()
        var replies = 0
        val handler = TestButtonInteractionHandler(query = { queries++; "result".toText() })
        repeat(2) { assertTrue(handler.handle(interaction(), { codes += it }, { _, _, _ -> replies++ })) }
        assertTrue(handler.handle(interaction().copy(interactionId = "new-click"), { codes += it }, { _, _, _ -> replies++ }))
        assertEquals(2, queries)
        assertEquals(2, replies)
        assertEquals(listOf(0, 0), codes)
    }

    @Test fun `group callback without button ID still works as in the official group example`() = runBlocking {
        val handler = TestButtonInteractionHandler(query = { "result".toText() })
        val codes = mutableListOf<Int>()
        var sent = false
        assertTrue(handler.handle(interaction().copy(buttonId = null), { codes += it }, { _, _, _ -> sent = true }))
        assertEquals(listOf(0), codes)
        assertTrue(sent)
    }

    @Test fun `owned invalid events fail acknowledgement without executing arbitrary commands`() = runBlocking {
        val base = interaction()
        val invalid = listOf(base.copy(chatType = 2), base.copy(replyEventId = ""),
            base.copy(groupId = null), base.copy(groupId = "not-an-openid"), base.copy(userId = null),
            base.copy(userId = "not-an-openid"), base.copy(buttonData = "/查询玩家 unexpected"),
            base.copy(buttonId = "other-button"))
        for (event in invalid) {
            val codes = mutableListOf<Int>()
            val handler = TestButtonInteractionHandler(query = { error("Must not execute") })
            assertTrue(handler.handle(event, { codes += it }, { _, _, _ -> error("Must not reply") }))
            assertEquals(listOf(1), codes)
        }
    }

    @Test fun `unrelated callback and non-button event are not intercepted or acknowledged`() = runBlocking {
        val handler = TestButtonInteractionHandler(query = { error("Must not execute") })
        val ignored = listOf(interaction().copy(buttonId = "other", buttonData = "other"),
            interaction().copy(type = 12), interaction().copy(interactionId = ""))
        for (event in ignored) assertFalse(handler.handle(event, { error("Must not ACK") }, { _, _, _ -> error("Must not reply") }))
    }

    @Test fun `failed acknowledgement never triggers a query`() = runBlocking<Unit> {
        val handler = TestButtonInteractionHandler(query = { error("Must not execute after ACK failure") })
        assertFailsWith<IllegalStateException> {
            handler.handle(interaction(), { throw IllegalStateException("fixture failure") }, { _, _, _ -> error("Must not reply") })
        }
    }

    @Test fun `missing command sends an understandable passive text response`() = runBlocking {
        val handler = TestButtonInteractionHandler(query = { null })
        var reply: Message? = null
        handler.handle(interaction(), {}, { _, _, message -> reply = message })
        assertEquals("测试命令未启用，请稍后重试".toText(), reply)
    }

    private inline fun <reified T> proxy(crossinline invoke: (Method, Array<out Any?>?) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args -> invoke(method, args) } as T

    @OptIn(FuzzyEventTypeImplementation::class)
    @Test fun `registered Simbot listener acknowledges and sends one SDK Markdown image keyboard reply`() = runBlocking {
        val order = mutableListOf<String>()
        val apis = mutableListOf<GroupMessageSendApi>()
        val config = proxy<love.forte.simbot.qguild.stdlib.BotConfiguration> { method, _ ->
            if (method.name == "getContentAsMarkdown") emptyMap<Any, Boolean>() else error("Unexpected configuration")
        }
        val sdkBot = proxy<love.forte.simbot.qguild.stdlib.Bot> { method, _ ->
            if (method.name == "getConfiguration") config else error("No SDK network allowed")
        }
        val bot = proxy<QGBot> { method, args -> when (method.name) {
            "getSource" -> sdkBot
            "executeData" -> {
                order += "send"
                apis += args!![0] as GroupMessageSendApi
                GroupMessageSendResult("fixture-reply", "2026-10-01T00:00:01Z")
            }
            else -> error("Unexpected bot access: ${method.name}")
        } }
        val source = source()
        val event = object : QGInteractionCreateEvent() {
            override val sourceEventEntity = source
            override val sourceEventRaw = "fixture"
            override val bot = bot
            override suspend fun respond(code: Int) { order += "ack:$interactionId:$code" }
        }
        val context = proxy<EventListenerContext> { method, _ ->
            if (method.name == "getEvent") event else error("Unexpected context access")
        }
        var listener: EventListener? = null
        var disposed = 0
        val handle = object : EventListenerRegistrationHandle { override fun dispose() { disposed++ } }
        val registrar = proxy<EventListenerRegistrar> { method, args ->
            if (method.name == "register") { listener = args!!.last() as EventListener; handle }
            else error("Unexpected registrar access")
        }
        val command = TestCommand(queryPlayer = { _, _ -> QGMarkdown.create("![img#1290px #2677px](https://example.com/fixture.png)") },
            publishImage = { _, _ -> error("Already published; do not upload twice") })
        val registration = TestButtonInteractionListener.register(registrar, TestButtonInteractionHandler(query = {
            order += "query"
            command.listen(it, emptyMap())
        }))
        listener!!.handleWith(context)
        listener!!.handleWith(context) // Transport retry, not a new click.
        assertEquals(listOf("ack:bare-interaction-fixture:0", "query", "send"), order)
        val api = apis.single()
        assertEquals("/v2/groups/$group/messages", api.url.encodedPath)
        val body = api.body as GroupAndC2CSendBody
        val wire = Json.parseToJsonElement(Json.encodeToString(body)).jsonObject
        assertEquals(outerId, wire["event_id"]?.jsonPrimitive?.content)
        assertNull(wire["msg_id"])
        assertEquals(1, wire["msg_seq"]?.jsonPrimitive?.int)
        assertEquals(2, wire["msg_type"]?.jsonPrimitive?.int)
        assertNull(wire["media"])
        assertEquals("test\n\n![img#1290px #2677px](https://example.com/fixture.png)",
            wire["markdown"]?.jsonObject?.get("content")?.jsonPrimitive?.content)
        val action = wire["keyboard"]!!.jsonObject["content"]!!.jsonObject["rows"]!!.jsonArray
            .single().jsonObject["buttons"]!!.jsonArray.single().jsonObject["action"]!!.jsonObject
        assertEquals(1, action["type"]?.jsonPrimitive?.int)
        assertEquals(TestCallbackProtocol.DATA, action["data"]?.jsonPrimitive?.content)
        assertNull(action["enter"])
        registration.dispose()
        assertEquals(1, disposed)
    }
}
