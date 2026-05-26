package cn.luorenmu.command.entity

import cn.luorenmu.command.CommandEvent
import cn.luorenmu.common.annotation.BotCommand

/**
 *
 * @author LoMu
 * Date 2025/11/26 23:28
 */
data class CommandInfo(
    val command: BotCommand,
    val commandEvent: CommandEvent,
)