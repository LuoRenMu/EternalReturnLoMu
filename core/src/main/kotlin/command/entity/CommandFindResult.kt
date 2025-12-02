package cn.luorenmu.command.entity

import cn.luorenmu.command.CommandEvent

/**
 *
 * @author LoMu
 * Date 2025/10/24 18:16
 */
data class CommandFindResult(
    val eventObj: CommandEvent,
    val commandParse: Map<String,String>
)
