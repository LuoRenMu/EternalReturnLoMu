package cn.luorenmu.command

import cn.luorenmu.Adapter
import cn.luorenmu.command.CommandRouter.Companion.COMMANDS
import cn.luorenmu.command.entity.CommandHelp
import cn.luorenmu.command.entity.CommandOptional
import cn.luorenmu.command.entity.MessageSender
import cn.luorenmu.common.annotation.BotCommand
import cn.luorenmu.common.util.PathUtils
import cn.luorenmu.render.RenderScreenshotPipeline
import love.forte.simbot.message.Message
import love.forte.simbot.message.OfflineImage

/**
 *
 * @author LoMu
 * Date 2025/12/25 11:20
 */

@BotCommand(name = "help", alias = "帮助", value = "",adapter = [Adapter.QG_BOT, Adapter.ONE_BOT])
class HelpCommand : CommandEvent {
    override val example: String = "/帮助"
    override val optionals: List<CommandOptional> = emptyList()
    override val description = "查询命令列表"

    private val outputPath by lazy {
        val commandEvent = COMMANDS.values.map {
            CommandHelp.CommandHelpItem(
                name = it.command.name,
                description = it.commandEvent.description,
                example = it.commandEvent.example,
                optionals = it.commandEvent.optionals
            )
        }

        val outputPath = PathUtils.resourcesPathResolve( "render", "help.png")
        RenderScreenshotPipeline.renderContentAndScreenshot(
            "help.ftl",
            CommandHelp(commandEvent),
            outputPath,
            "#content-container"
        )
        outputPath
    }
    override suspend fun listen(
        sender: MessageSender,
        command: Map<String, String>,
    ): Message {
        return OfflineImage.fileOfflineImage(outputPath.toString())
    }

}
