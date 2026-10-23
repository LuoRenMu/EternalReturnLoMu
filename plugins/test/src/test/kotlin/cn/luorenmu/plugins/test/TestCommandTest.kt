package cn.luorenmu.plugins.test

import cn.luorenmu.plugins.qqmedia.PublishedQQImage
import cn.luorenmu.Adapter
import cn.luorenmu.command.entity.MessageSender
import cn.luorenmu.command.plugin.CommandPlugin
import cn.luorenmu.common.annotation.BotCommand
import kotlinx.coroutines.runBlocking
import love.forte.simbot.common.id.ID
import love.forte.simbot.common.id.StringID
import love.forte.simbot.component.qguild.message.QGKeyboards
import love.forte.simbot.component.qguild.message.QGMarkdown
import love.forte.simbot.component.qguild.message.MessageParsers
import love.forte.simbot.component.qguild.message.SendingMessageParser
import love.forte.simbot.component.qguild.bot.QGBot
import love.forte.simbot.qguild.api.message.GroupAndC2CSendBody
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int
import love.forte.simbot.message.OfflineImage
import love.forte.simbot.message.OfflineResourceImage
import love.forte.simbot.message.PlainText
import love.forte.simbot.message.Message
import love.forte.simbot.message.toText
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import love.forte.simbot.message.Messages
import java.util.ServiceLoader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.assertSame
import kotlin.test.assertNull
import kotlin.test.assertNotNull

class TestCommandTest {
    @TempDir lateinit var root: Path

    private fun sender(): MessageSender {
        val id = StringID::class.java.getMethod("valueOf", String::class.java).invoke(null, "test-user") as ID
        return MessageSender(id, "tester", id, "/test", "/test")
    }

    private fun image(): OfflineResourceImage {
        val path = root.resolve("player.png")
        Files.write(path, Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a0XcAAAAASUVORK5CYII="))
        return assertIs<OfflineResourceImage>(OfflineImage.fileOfflineImage(path.toString()))
    }

    private fun command(query: suspend (MessageSender, String) -> Message?) = TestCommand(
        queryPlayer = query,
        publishImage = { _, _ -> PublishedQQImage("https://images.example/test.png", 1, 1) },
    )

    @Test
    fun `test is restricted to qgbot and plugin is discoverable`() {
        val annotation = TestCommand::class.java.getAnnotation(BotCommand::class.java)
        assertEquals("test", annotation.alias)
        assertEquals(listOf(Adapter.QG_BOT), annotation.adapter.toList())
        assertEquals(false, TestCommand().optionals.single().required)
        assertTrue(ServiceLoader.load(CommandPlugin::class.java).any { it is TestPlugin })
    }

    @Test
    fun `reply contains Markdown image and callback button without input box commands`() = runBlocking<Unit> {
        val image = image()
        var queried: String? = null
        val command = command { _, nickname -> queried = nickname; image }
        val elements = assertIs<Messages>(command.listen(sender(), emptyMap())).toList()
        assertEquals("백수", queried)
        assertEquals(2, elements.size)
        assertEquals("test\n\n![img#1px #1px](https://images.example/test.png)",
            assertIs<QGMarkdown>(elements[0]).markdown.content)
        assertTrue(elements.none { it is OfflineImage || it is PlainText })
        val rows = assertIs<QGKeyboards>(elements[1]).keyboards.content.rows
        assertEquals(1, rows.size)
        val button = rows.single().buttons.single()
        assertEquals("test", button.renderData?.label)
        assertEquals(1, button.action?.type)
        assertEquals(2, button.action?.permission?.type)
        assertEquals(TestCallbackProtocol.DATA, button.action?.data)
        assertNull(button.action?.enter)
        assertNull(button.action?.reply)
        assertEquals(null, button.action?.anchor)
    }

    @Test fun `optional nickname delegates and captions are excluded from the test image`() = runBlocking<Unit> {
        val image = image()
        var queried: String? = null
        val command = command { _, nickname -> queried = nickname; Messages.of(image, "expiry text".toText()) }
        val elements = assertIs<Messages>(command.listen(sender(), mapOf("nickname" to "KCW"))).toList()
        assertEquals("KCW", queried)
        assertIs<QGMarkdown>(elements.first())
        assertEquals(2, elements.size)
    }

    @Test fun `query errors are returned without a fake image or button`() = runBlocking<Unit> {
        val error = "玩家不存在".toText()
        assertSame(error, command { _, _ -> error }.listen(sender(), emptyMap()))
        assertTrue(assertIs<PlainText>(command { _, _ -> null }.listen(sender(), emptyMap())).text.contains("未启用"))
    }

    @Test fun `published player Markdown is reused but its execute buttons are replaced with test callback button`() = runBlocking<Unit> {
        val url = "https://cos.example/player.png?sign=fixture"
        val image = "![img#1290px #2677px]($url)"
        val markdown = QGMarkdown.create(image)
        val player = Messages.of(markdown, QGKeyboards.create(
            love.forte.simbot.qguild.model.MessageKeyboard.builder("match").renderData("查询对局", "查询对局", 1)
                .action(type = 2, permission = love.forte.simbot.qguild.model.MessageKeyboard.ActionPermission.AllAccessible,
                    data = "/查询对局 1 백수 41", enter = true, reply = false).build()))
        for (result in listOf<Message>(player, markdown)) {
            val command = TestCommand(queryPlayer = { _, _ -> result }, publishImage = { _, _ -> error("Already published") })
            val reply = assertIs<Messages>(command.listen(sender(), emptyMap()))
            assertEquals("test\n\n$image", assertIs<QGMarkdown>(reply.first()).markdown.content)
            val button = reply.filterIsInstance<QGKeyboards>().single().keyboards.content.rows.single().buttons.single()
            assertEquals(TestCallbackProtocol.DATA, button.action?.data)
            assertEquals(1, button.action?.type)
            assertNull(button.action?.enter)
        }
    }

    @Test fun `SDK produces exactly one type 2 Markdown payload including image and keyboard without media upload`() = runBlocking<Unit> {
        val configuration = java.lang.reflect.Proxy.newProxyInstance(QGBot::class.java.classLoader,
            arrayOf(love.forte.simbot.qguild.stdlib.BotConfiguration::class.java)) { _, method, _ ->
            if (method.name == "getContentAsMarkdown") emptyMap<Any, Boolean>() else error("Unexpected config: ${method.name}")
        }
        val source = java.lang.reflect.Proxy.newProxyInstance(QGBot::class.java.classLoader,
            arrayOf(love.forte.simbot.qguild.stdlib.Bot::class.java)) { _, method, _ ->
            if (method.name == "getConfiguration") configuration else error("No network: ${method.name}")
        }
        val bot = java.lang.reflect.Proxy.newProxyInstance(QGBot::class.java.classLoader, arrayOf(QGBot::class.java)) {
            _, method, _ -> when (method.name) {
                "getSource" -> source
                else -> error("Markdown must not upload Base64 media or access network: ${method.name}")
            }
        } as QGBot
        val image = image()
        val reply = command { _, _ -> image }.listen(sender(), emptyMap())
        val bodies = MessageParsers.parseToGroupAndC2C(bot, reply, SendingMessageParser.GroupBuilderType.GROUP,
            "fixture-group", { GroupAndC2CSendBody.create("", 0) }, {}, {})
        val body = bodies.single()
        assertEquals(2, body.msgType)
        assertNull(body.media)
        assertEquals("test\n\n![img#1px #1px](https://images.example/test.png)", assertNotNull(body.markdown).content)
        assertNotNull(body.keyboards)
        assertEquals(TestCallbackProtocol.DATA, body.keyboards?.content?.rows?.single()?.buttons?.single()?.action?.data)
        // Verify the wire field, not just an in-memory keyboard object. Client rendering is a separate capability.
        val wire = Json.parseToJsonElement(Json.encodeToString(body)).jsonObject
        assertNotNull(wire["keyboard"], "SDK must serialize the actual QQ keyboard field")
        assertNull(wire["media"])
        assertNotNull(wire["markdown"])
        val action = wire["keyboard"]!!.jsonObject["content"]!!.jsonObject["rows"]!!.jsonArray
            .single().jsonObject["buttons"]!!.jsonArray.single().jsonObject["action"]!!.jsonObject
        assertEquals(1, action["type"]!!.jsonPrimitive.int)
        assertNull(action["enter"])
    }
}
