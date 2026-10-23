package cn.luorenmu.plugins.qqmedia

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import love.forte.simbot.component.qguild.bot.QGBot
import love.forte.simbot.component.qguild.message.QGKeyboards
import love.forte.simbot.component.qguild.message.QGMarkdown
import love.forte.simbot.component.qguild.message.QGMedia
import love.forte.simbot.message.Messages
import love.forte.simbot.message.OfflineImage
import love.forte.simbot.message.toText
import love.forte.simbot.qguild.api.message.GroupAndC2CSendBody
import love.forte.simbot.qguild.api.message.group.GroupMessageSendApi
import love.forte.simbot.qguild.api.message.group.GroupMessageSendResult
import love.forte.simbot.qguild.model.MessageKeyboard
import love.forte.simbot.qguild.model.MessageMedia
import org.junit.jupiter.api.io.TempDir
import java.lang.reflect.Proxy
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import kotlin.test.*

class QQGroupInteractionReplySenderTest {
    @Test fun `active image uses SDK upload without passive reply identifiers`() = runBlocking {
        val path = root.resolve("active-match.png")
        Files.write(path, Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a0XcAAAAASUVORK5CYII="))
        var uploads = 0
        val sent = mutableListOf<GroupMessageSendApi>()
        QQProactiveGroupSender.send(bot(onUpload = { uploads++ }, onSend = { sent += it }), group,
            OfflineImage.fileOfflineImage(path.toString()))
        assertEquals(1, uploads)
        val body = sent.single().body as GroupAndC2CSendBody
        assertEquals(7, body.msgType)
        assertEquals("fixture-info", body.media?.fileInfo)
        val wire = Json.parseToJsonElement(Json.encodeToString(body)).jsonObject
        assertNull(wire["msg_id"])
        assertNull(wire["event_id"])
    }

    @TempDir lateinit var root: Path
    private val group = "A".repeat(32)
    private val eventId = "INTERACTION_CREATE:outer-fixture"
    private inline fun <reified T> proxy(crossinline call: (String, Array<out Any?>?) -> Any?) =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args -> call(method.name, args) } as T
    private fun bot(onUpload: (Array<out Any?>) -> Unit = {}, onSend: (GroupMessageSendApi) -> Unit = {}): QGBot {
        val config = proxy<love.forte.simbot.qguild.stdlib.BotConfiguration> { name, _ ->
            if (name == "getContentAsMarkdown") emptyMap<Any, Boolean>() else error("Unexpected config") }
        val source = proxy<love.forte.simbot.qguild.stdlib.Bot> { name, _ ->
            if (name == "getConfiguration") config else error("No SDK network") }
        return proxy { name, args -> when (name) {
            "getSource" -> source
            "uploadGroupMedia" -> {
                onUpload(args!!)
                QGMedia(Json.decodeFromString(MessageMedia.serializer(), """{"file_uuid":"fixture","file_info":"fixture-info","ttl":300}"""))
            }
            "executeData" -> {
                onSend(args!![0] as GroupMessageSendApi)
                GroupMessageSendResult("fixture", "2026-10-01T00:00:00Z")
            }
            else -> error("Unexpected bot access: $name")
        } }
    }

    private fun assertPassive(body: GroupAndC2CSendBody) {
        val wire = Json.parseToJsonElement(Json.encodeToString(body)).jsonObject
        assertEquals(eventId, wire["event_id"]?.jsonPrimitive?.content)
        assertNull(wire["msg_id"])
        assertEquals(1, wire["msg_seq"]?.jsonPrimitive?.int)
    }

    @Test fun `Markdown image and callback keyboard remain one passive SDK body`() = runBlocking<Unit> {
        val button = MessageKeyboard.builder("fixture").renderData("查询", "查询", 1).action(type = 1,
            permission = MessageKeyboard.ActionPermission.AllAccessible, data = "fixture").build()
        val reply = Messages.of(QGMarkdown.create("![img#1440px #800px](https://example.com/fixture.png)"), QGKeyboards.create(button))
        val body = QQGroupInteractionReplySender.prepareBodies(bot(), group, eventId, reply).single()
        assertPassive(body)
        assertEquals(2, body.msgType)
        assertNotNull(body.markdown)
        assertNotNull(body.keyboards)
        assertNull(body.media)
    }

    @Test fun `code text is sent directly through Simbot with outer event ID`() = runBlocking<Unit> {
        val sent = mutableListOf<GroupMessageSendApi>()
        QQGroupInteractionReplySender.send(bot(onSend = { sent += it }), group, eventId, "兑换码 fixture".toText())
        val api = sent.single()
        assertEquals("/v2/groups/$group/messages", api.url.encodedPath)
        val body = api.body as GroupAndC2CSendBody
        assertPassive(body)
        assertEquals(0, body.msgType)
        assertEquals("兑换码 fixture", body.content)
    }

    @Test fun `settlement offline image uses the SDK group upload and a single passive media reply`() = runBlocking<Unit> {
        val path = root.resolve("match.png")
        Files.write(path, Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a0XcAAAAASUVORK5CYII="))
        var uploads = 0
        val sent = mutableListOf<GroupMessageSendApi>()
        val bot = bot(onUpload = { args -> uploads++; assertEquals(group, args[0].toString()) }, onSend = { sent += it })
        QQGroupInteractionReplySender.send(bot, group, eventId, OfflineImage.fileOfflineImage(path.toString()))
        assertEquals(1, uploads)
        val body = sent.single().body as GroupAndC2CSendBody
        assertPassive(body)
        assertEquals(7, body.msgType)
        assertEquals("fixture-info", assertNotNull(body.media).fileInfo)
    }
}
