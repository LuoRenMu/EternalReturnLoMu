package cn.luorenmu.plugins.player

import cn.luorenmu.command.plugin.CommandPlugin

/**
 *
 * @author LoMu
 * Date 2026/8/16 15:30
 */
class PlayerPlugin : CommandPlugin {
    override val id = "player"
    override val name = "玩家查询"
    override val version = "1.0.0"
    override val commands = listOf(SearchPlayerCommand(), PlayerAliasCommand())
}
