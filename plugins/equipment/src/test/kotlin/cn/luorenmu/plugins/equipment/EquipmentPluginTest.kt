package cn.luorenmu.plugins.equipment

import cn.luorenmu.command.plugin.CommandPlugin
import cn.luorenmu.common.annotation.BotCommand
import java.util.ServiceLoader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EquipmentPluginTest {
    @Test
    fun `query equipment belongs only to discovered equipment plugin`() {
        val plugin = ServiceLoader.load(CommandPlugin::class.java).first { it.id == "equipment" }
        assertEquals("装备查询", plugin.name)
        val command = plugin.commands.single()
        assertTrue(command is ItemQueryCommand)
        assertEquals("查询装备", assertNotNull(command.javaClass.getAnnotation(BotCommand::class.java)).alias)
    }
}
