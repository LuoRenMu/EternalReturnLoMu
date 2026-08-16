package cn.luorenmu.plugins.character

import cn.luorenmu.command.plugin.CommandPlugin

/**
 *
 * @author LoMu
 * Date 2026/8/16 15:30
 */
class CharacterPlugin : CommandPlugin {
    override val id = "character"
    override val name = "角色数据"
    override val version = "1.0.0"
    override val commands = listOf(CharacterStatsCommand(), CharacterDetailCommand())
}
