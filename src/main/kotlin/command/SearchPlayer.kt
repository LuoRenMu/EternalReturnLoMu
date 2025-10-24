package cn.luorenmu.command

import cn.luorenmu.command.entity.MessageSender
import cn.luorenmu.common.annotation.CommandFilter
import love.forte.simbot.message.Message
import love.forte.simbot.message.toText

/**
 *
 * @author LoMu
 * Date 2025/10/24 14:06
 */

@CommandFilter("search <nickname> <t> <t>")
class SearchPlayer : CommandEvent {
    override suspend fun listen(sender: MessageSender): Message {
        return "查查${sender.command["nickname"]}".toText()
    }

}