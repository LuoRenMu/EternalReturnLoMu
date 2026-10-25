package cn.luorenmu.plugins.match

import cn.luorenmu.command.CommandEvent
import cn.luorenmu.command.plugin.CommandPlugin

/** Live settlement query; design examples never enter the command path. */
class MatchPlugin : CommandPlugin {
    override val id = "match"
    override val name = "对局详情"
    override val version = "1.1.2"
    override val commands: List<CommandEvent> = listOf(MatchDetailCommand())
}
