package cn.luorenmu.plugins.player

import cn.luorenmu.repository.entity.EternalReturnNewsRecord
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import love.forte.simbot.component.qguild.bot.QGBot
import love.forte.simbot.component.qguild.message.MessageParsers
import love.forte.simbot.component.qguild.message.QGKeyboards
import love.forte.simbot.component.qguild.message.QGMarkdown
import love.forte.simbot.component.qguild.message.QGMedia
import love.forte.simbot.component.qguild.message.SendingMessageParser
import love.forte.simbot.message.Messages
import love.forte.simbot.message.OfflineImage
import love.forte.simbot.message.PlainText
import love.forte.simbot.qguild.api.message.GroupAndC2CSendBody
import love.forte.simbot.qguild.model.MessageMedia
import org.junit.jupiter.api.io.TempDir
import java.lang.reflect.Proxy
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.Base64
import kotlin.test.*

class PlayerImageReplyTest {
    @TempDir lateinit var root: Path
    private val now = LocalDateTime.of(2026, 10, 1, 12, 0)
    private fun image(): OfflineImage {
        val path = root.resolve("player.png")
        Files.write(path, Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a0XcAAAAASUVORK5CYII="))
        return OfflineImage.fileOfflineImage(path.toString())
    }

    private fun codes() = listOf(
        EternalReturnNewsRecord(articleId = 1, title = "new", code = "NEW", processedAt = now,
            eventStartTime = now.minusDays(1), eventEndTime = now.plusDays(3)),
        EternalReturnNewsRecord(articleId = 2, title = "ending", code = "END", processedAt = now.minusDays(1),
            eventStartTime = now.minusDays(1), eventEndTime = now.plusHours(1)),
    )

    @Test fun `no notice returns the identical original offline image`() {
        val image = image()
        assertSame(image, playerReplyWithRedemptionCodes(image, emptyList(), now))
    }

    @Test fun `reply contains only original image and coupon strings without Markdown or buttons`() {
        val image = image()
        val reply = assertIs<Messages>(playerReplyWithRedemptionCodes(image, codes(), now)).toList()
        assertEquals(2, reply.size)
        assertSame(image, reply.first())
        assertEquals("发现新的兑换码:NEW\n即将过期的兑换码:END", assertIs<PlainText>(reply.last()).text)
        assertTrue(reply.none { it is QGKeyboards || it is QGMarkdown })
    }

    private inline fun <reified T> proxy(crossinline call: (String) -> Any?) =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
            call(method.name)
        } as T

    @Test fun `QQ SDK uses offline media upload and plain text without public image Markdown or keyboard`() = runBlocking<Unit> {
        var uploads = 0
        val configuration = proxy<love.forte.simbot.qguild.stdlib.BotConfiguration> { name ->
            if (name == "getContentAsMarkdown") emptyMap<Any, Boolean>() else error("Unexpected config")
        }
        val source = proxy<love.forte.simbot.qguild.stdlib.Bot> { name ->
            if (name == "getConfiguration") configuration else error("No network")
        }
        val bot = proxy<QGBot> { name -> when (name) {
            "getSource" -> source
            "uploadGroupMedia" -> {
                uploads++
                QGMedia(Json.decodeFromString(MessageMedia.serializer(),
                    """{"file_uuid":"fixture","file_info":"fixture-info","ttl":300}"""))
            }
            else -> error("Unexpected bot access: $name")
        } }
        val bodies = MessageParsers.parseToGroupAndC2C(bot,
            playerReplyWithRedemptionCodes(image(), codes(), now), SendingMessageParser.GroupBuilderType.GROUP,
            "fixture-group", { GroupAndC2CSendBody.create("", 0) }, {}, {})
        assertEquals(1, uploads)
        assertTrue(bodies.all { it.keyboards == null && it.markdown == null })
        assertEquals("fixture-info", bodies.single { it.media != null }.media?.fileInfo)
        assertEquals(7, bodies.single { it.media != null }.msgType)
        // Simbot inserts a space where the preceding image element was parsed.
        assertEquals("发现新的兑换码:NEW\n即将过期的兑换码:END", bodies.mapNotNull { it.content }
            .filter { it.isNotBlank() }.joinToString("").trim())
    }

    @Test fun `player plugin lifecycle needs no QQ callback application`() {
        val plugin = PlayerPlugin()
        repeat(2) { plugin.onEnable(); plugin.onDisable() }
        assertEquals("1.6.1", plugin.version)
        assertEquals(listOf(SearchPlayerCommand::class, PlayerAliasCommand::class), plugin.commands.map { it::class })
    }
}
