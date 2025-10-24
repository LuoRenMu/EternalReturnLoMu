package cn.luorenmu.command.entity

import love.forte.simbot.common.id.ID
import love.forte.simbot.component.qguild.event.QGGroupAtMessageCreateEvent

/**
 *
 * @author LoMu
 * Date 2025/10/24 13:46
 */
data class MessageSender(
    // openId 是一串经过处理的QQ号 无法知晓原始QQ号
    var groupOpenId: ID,
    var senderName: String,
    var senderOpenId: ID,
    var message: String,
    var command: Map<String,String>
) {
    companion object {
        suspend fun builder(event: QGGroupAtMessageCreateEvent,parseCommand : Map<String,String>)  =
            MessageSender(
                groupOpenId = event.content().id,
                senderName = event.author().name,
                senderOpenId = event.authorId,
                message = event.messageContent.plainText,
                command = parseCommand
            )
    }
}
