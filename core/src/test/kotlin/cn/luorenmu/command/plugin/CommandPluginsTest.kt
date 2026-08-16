package cn.luorenmu.command.plugin

import cn.luorenmu.Adapter
import cn.luorenmu.command.CommandEvent
import cn.luorenmu.command.HelpCommand
import cn.luorenmu.command.entity.CommandOptional
import cn.luorenmu.command.entity.MessageSender
import cn.luorenmu.common.annotation.BotCommand
import love.forte.simbot.message.Message
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 *
 * @author LoMu
 * Date 2026/8/16 15:30
 */
class CommandPluginsTest {
    @Test
    fun `disable reply and state survive a loader restart`() {
        val directory = Files.createTempDirectory("lomu-plugin-test")
        CommandPlugins.configureBuiltins(listOf(CommandPluginFactory { TestPlugin() }))
        CommandPlugins.initialize(Adapter.ONE_BOT, directory)

        assertNotNull(CommandPlugins.commands()["plugin-test"])
        CommandPlugins.setDisabledReply("test", "maintenance")
        CommandPlugins.disable("test")
        assertNull(CommandPlugins.commands()["plugin-test"])
        assertTrue(CommandPlugins.commands().values.any { it.commandEvent is HelpCommand })
        assertEquals("maintenance", CommandPlugins.disabledCommand("/plugin-test anything")?.reply)

        CommandPlugins.configureBuiltins(listOf(CommandPluginFactory { TestPlugin() }))
        CommandPlugins.initialize(Adapter.ONE_BOT, directory)
        assertNull(CommandPlugins.commands()["plugin-test"], "disabled state must survive restart")
        assertEquals("maintenance", CommandPlugins.views().first { it.id == "test" }.disabledReply)

        CommandPlugins.enable("test")
        assertNotNull(CommandPlugins.commands()["plugin-test"])
        assertTrue(CommandPlugins.views().first { it.id == "test" }.enabled)
    }

    @Test
    fun `core cannot be disabled or assigned a disabled reply`() {
        CommandPlugins.configureBuiltins(emptyList())
        CommandPlugins.initialize(Adapter.QG_BOT, Files.createTempDirectory("lomu-core-plugin-test"))
        assertFailsWith<IllegalArgumentException> { CommandPlugins.disable("core") }
        assertFailsWith<IllegalArgumentException> { CommandPlugins.setDisabledReply("core", "off") }
        assertFalse(CommandPlugins.views().first { it.id == "core" }.external)
    }

    private class TestPlugin : CommandPlugin {
        override val id = "test"
        override val name = "Test"
        override val version = "1"
        override val commands = listOf(TestCommand())
    }

    @BotCommand(name = "test", alias = "plugin-test", value = "")
    private class TestCommand : CommandEvent {
        override val description = "test"
        override val example = "/plugin-test"
        override val optionals = emptyList<CommandOptional>()
        override suspend fun listen(sender: MessageSender, command: Map<String, String>): Message? = null
    }
}
