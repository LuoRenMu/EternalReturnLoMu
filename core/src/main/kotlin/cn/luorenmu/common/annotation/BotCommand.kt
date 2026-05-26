package cn.luorenmu.common.annotation

import cn.luorenmu.Adapter

/**
 *
 * @author LoMu
 * Date 2025/12/2 20:25
 */

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class BotCommand(
    val name: String,
    val alias: String,
    val value: String,
    val adapter: Array<Adapter> = [Adapter.ONE_BOT, Adapter.QG_BOT],
)