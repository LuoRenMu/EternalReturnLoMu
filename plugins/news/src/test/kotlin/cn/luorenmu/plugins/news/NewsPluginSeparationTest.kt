package cn.luorenmu.plugins.news

import kotlin.test.Test
import kotlin.test.assertEquals

class NewsPluginSeparationTest {
    @Test fun `news keeps only redemption and activity commands after extracting patch notes`() {
        val plugin = NewsPlugin()
        assertEquals("1.3.1", plugin.version)
        assertEquals(listOf(RedemptionCodeCommand::class.java, RedemptionCodeActivityCommand::class.java),
            plugin.commands.map { it.javaClass })
    }
}
