package cn.luorenmu.command.entity

import cn.luorenmu.command.CommandEvent
import cn.luorenmu.common.annotation.BotCommand

/**
 *
 * @author LoMu
 * Date 2025/10/24 18:16
 */
data class CommandFindResult(
    val eventObj: CommandEvent,
    val command: BotCommand,
    val commandParse: Map<String,String>
)
