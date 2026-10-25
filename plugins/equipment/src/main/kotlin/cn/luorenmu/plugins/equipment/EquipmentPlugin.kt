package cn.luorenmu.plugins.equipment

import cn.luorenmu.command.plugin.CommandPlugin

class EquipmentPlugin : CommandPlugin {
    override val id = "equipment"
    override val name = "装备查询"
    override val version = "1.1.1"
    override val commands = listOf(ItemQueryCommand())
}
