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

@BotCommand(id = "help", alias = "help", value = "")
class HelpCommand: CommandEvent{
    override suspend fun listen(
        sender: MessageSender,
        command: Map<String, String>,
    ): Message? {
        return null
    }

}
