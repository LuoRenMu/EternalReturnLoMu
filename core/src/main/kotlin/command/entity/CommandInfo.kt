package cn.luorenmu.command.entity

import cn.luorenmu.command.CommandEvent
import cn.luorenmu.common.annotation.CommandFilter

/**
 *
 * @author LoMu
 * Date 2025/11/26 23:28
 */
data class CommandInfo (
    val commandFilter: CommandFilter,
    val commandEvent: CommandEvent
)