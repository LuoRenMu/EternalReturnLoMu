package cn.luorenmu.plugins.test

import cn.luorenmu.Adapter
import cn.luorenmu.command.CommandEvent
import cn.luorenmu.command.CommandRouter
import cn.luorenmu.command.entity.CommandOptional
import cn.luorenmu.command.entity.MessageSender
import cn.luorenmu.common.annotation.BotCommand
import cn.luorenmu.plugins.qqmedia.PublishedQQImage
import cn.luorenmu.plugins.qqmedia.QQImagePublisher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import love.forte.simbot.component.qguild.message.QGKeyboards
import love.forte.simbot.component.qguild.message.QGMarkdown
import love.forte.simbot.message.Image
import love.forte.simbot.message.Message
import love.forte.simbot.message.Messages
import love.forte.simbot.message.toText
import love.forte.simbot.qguild.model.MessageKeyboard

@BotCommand("Markdown 战绩按钮测试", "test", "<nickname>", adapter = [Adapter.QG_BOT])
class TestCommand(
    private val queryPlayer: suspend (MessageSender, String) -> Message? = { sender, nickname ->
        val router = CommandRouter()
        val text = "/查询玩家 $nickname"
        val target = router.commandFind(text)
        target?.eventObj?.listen(sender.copy(message = text, plainText = text), target.commandParse)
    },
    private val publishImage: suspend (Image, MessageSender) -> PublishedQQImage = { image, sender ->
        withContext(Dispatchers.IO) {
            QQImagePublisher.publish(image, sender.groupOpenId.toString())
        }
    },
) : CommandEvent {
    override val description = "测试 Markdown 战绩图片与回调按钮同条发送；不填昵称默认查询 백수，点击后直接执行 /test 并回复"
    override val example = "/test 백수"
    override val optionals = listOf(CommandOptional("nickname", "玩家昵称，默认 백수", required = false))

    override suspend fun listen(sender: MessageSender, command: Map<String, String>): Message {
        val nickname = command["nickname"]?.trim()?.takeIf { it.isNotEmpty() } ?: "백수"
        val result = queryPlayer(sender, nickname) ?: return "玩家查询插件未启用，无法生成测试战绩图片".toText()
        // Player queries now publish their own Markdown image. Reuse it without another upload or its buttons.
        val markdown = when (result) {
            is QGMarkdown -> result
            is Messages -> result.filterIsInstance<QGMarkdown>().firstOrNull()
            else -> null
        }
        val existingImage = markdown?.markdown?.content?.let {
            Regex("!\\[img#\\d+px #\\d+px\\]\\(https://[^)]+\\)").find(it)?.value
        }
        if (existingImage != null) return Messages.of(QGMarkdown.create("test\n\n$existingImage"), CALLBACK_BUTTON)
        val image = when (result) {
            is Image -> result
            is Messages -> result.filterIsInstance<Image>().firstOrNull()
            else -> null
        } ?: return result
        val published = publishImage(image, sender)
        // The image is inside Markdown, not a separate OfflineImage element; the SDK emits one type-2 payload.
        return Messages.of(QGMarkdown.create(
            "test\n\n![img#${published.width}px #${published.height}px](${published.url})"), CALLBACK_BUTTON)
    }

    private companion object {
        val CALLBACK_BUTTON = QGKeyboards.create(
            MessageKeyboard.builder(TestCallbackProtocol.BUTTON_ID)
                .renderData("test", "test", 1)
                .action(
                    type = 1,
                    permission = MessageKeyboard.ActionPermission.AllAccessible,
                    data = TestCallbackProtocol.DATA,
                    unsupportTips = "当前 QQ 客户端不支持此按钮，请手动输入 /test",
                )
                .build(),
        )
    }
}
