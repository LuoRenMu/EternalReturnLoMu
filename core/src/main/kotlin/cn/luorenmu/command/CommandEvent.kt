package cn.luorenmu.command

import cn.luorenmu.command.entity.CommandOptional
import cn.luorenmu.command.entity.MessageSender
import love.forte.simbot.message.Message

/**
 *
 * @author LoMu
 * Date 2025/10/24 13:27
 */
interface CommandEvent {
    val description: String
    val example: String
    val optionals: List<CommandOptional>
    suspend fun listen(sender: MessageSender, command: Map<String, String>): Message?
}