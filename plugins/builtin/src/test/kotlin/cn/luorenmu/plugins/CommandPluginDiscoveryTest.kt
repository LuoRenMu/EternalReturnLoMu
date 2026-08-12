package cn.luorenmu.plugins

import cn.luorenmu.Adapter
import cn.luorenmu.command.plugin.CommandPlugins
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class CommandPluginDiscoveryTest {
    @Test
    fun `all classpath plugins are discovered without a central registry`() {
        CommandPlugins.initialize(Adapter.ONE_BOT, Files.createTempDirectory("plugin-discovery-test"))

        assertEquals(
            setOf("core", "character", "player", "tier", "news", "query-statistics"),
            CommandPlugins.views().map { it.id }.toSet(),
        )
    }
}
