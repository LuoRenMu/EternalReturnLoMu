package cn.luorenmu.qqbot.listen

import cn.luorenmu.command.CommandRouter
import cn.luorenmu.command.RecentMessageGuard
import cn.luorenmu.command.entity.MessageSender
import cn.luorenmu.listen.EventHandle
import love.forte.simbot.component.qguild.event.QGGroupAtMessageCreateEvent
import love.forte.simbot.event.Event
import love.forte.simbot.component.qguild.group.QGGroupRole

/**
 * @author LoMu
 * Date 2025/10/22 23:00
 */

/**
 * 官方BOT只有AT事件附带的消息才会发送给机器人
 */
class GroupAtMessageCreateListen : EventHandle {

    private val commandListenAllocator = CommandRouter()
    private val recentMessages = RecentMessageGuard()
    override suspend fun handle(event: Event) {
        val atEvent = event as QGGroupAtMessageCreateEvent
        if (!recentMessages.shouldProcess(atEvent.messageContent.id.toString())) return

        val reply = commandListenAllocator.call(
            MessageSender(
                groupOpenId = event.content().id,
                senderName = event.author().name,
                senderOpenId = event.authorId,
                message = event.messageContent.messages.toString(),
                plainText = event.messageContent.plainText,
            ).apply { qqGroupMemberRole = confirmedQQGroupRole(atEvent.author().memberRole) }
        )

        reply?.let { atEvent.reply(it) } ?: run {
            atEvent.reply("命令格式错误或不存在,请确保指令之间存在空格符")
        }
    }

}

/** SDK 4.5 defaults a missing member_role to MEMBER, so that value cannot confirm ordinary membership. */
internal fun confirmedQQGroupRole(role: QGGroupRole): String? = when (role) {
    QGGroupRole.OWNER -> "owner"
    QGGroupRole.ADMIN -> "admin"
    QGGroupRole.MEMBER -> null
}
