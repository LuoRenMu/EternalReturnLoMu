package command

import cn.luorenmu.command.CommandEvent
import cn.luorenmu.command.CommandRouter.Companion.COMMANDS
import cn.luorenmu.command.entity.MessageSender
import cn.luorenmu.common.annotation.BotCommand
import love.forte.simbot.message.Message
import love.forte.simbot.message.toText

/**
 * 
 * @author LoMu
 * Date 2025/12/25 11:20
 */

@BotCommand(name = "help", alias = "帮助", value = "",description = "帮助")
class HelpCommand: CommandEvent{
    override suspend fun listen(
        sender: MessageSender,
        command: Map<String, String>,
    ): Message? {
        val append = StringBuilder().append("命令列表:").append("\n")
        COMMANDS.values.filter { it.command.name != "help" }.forEach {
            val help = it.command.description
            val name = it.command.name
            append.append("名称: $name").append("\n")
                .append("描述: $help").append("\n")
        }
        return null
    }

}
