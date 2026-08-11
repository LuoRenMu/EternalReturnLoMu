package cn.luorenmu.plugins.character

import cn.luorenmu.command.plugin.CommandPlugin

class CharacterPlugin : CommandPlugin {
    override val id = "character"
    override val name = "角色数据"
    override val version = "1.0.0"
    override val commands = listOf(CharacterStatsCommand(), CharacterDetailCommand())
}
