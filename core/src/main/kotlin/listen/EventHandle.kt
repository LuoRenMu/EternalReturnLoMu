package cn.luorenmu.listen

import love.forte.simbot.event.Event

/**
 *
 * @author LoMu
 * Date 2025/10/23 13:06
 */
interface EventHandle {
    suspend fun handle(event: Event)
}