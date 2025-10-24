package cn.luorenmu.listen

import cn.luorenmu.command.CommandListenAllocator
import cn.luorenmu.command.annotation.EventDefine
import love.forte.simbot.component.qguild.event.QGGroupAtMessageCreateEvent
import love.forte.simbot.event.Event


/**
 * @author LoMu
 * Date 2025/10/22 23:00
 */

/**
 * 官方BOT只有AT事件附带的消息才会发送给机器人
 */
@EventDefine(QGGroupAtMessageCreateEvent::class)
class GroupAtMessageCreateListen : EventHandle {


    override suspend fun handle(event: Event) {
        CommandListenAllocator().call(event)
    }

}