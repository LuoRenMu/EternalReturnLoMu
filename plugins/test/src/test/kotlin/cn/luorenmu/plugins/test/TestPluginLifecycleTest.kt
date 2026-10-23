package cn.luorenmu.plugins.test

import love.forte.simbot.event.EventListenerRegistrationHandle
import kotlin.test.Test
import kotlin.test.assertEquals

class TestPluginLifecycleTest {
    @Test fun `enable registers once and disable disposes once including after a hot reload`() {
        var registrations = 0
        var disposals = 0
        val plugin = TestPlugin {
            registrations++
            object : EventListenerRegistrationHandle { override fun dispose() { disposals++ } }
        }
        repeat(2) { plugin.onEnable() }
        assertEquals(1, registrations)
        assertEquals(0, disposals)
        repeat(2) { plugin.onDisable() }
        assertEquals(1, disposals)
        plugin.onEnable()
        assertEquals(2, registrations)
        plugin.onDisable()
        assertEquals(2, disposals)
    }

    @Test fun `unavailable QQ runtime can be disabled safely`() {
        val plugin = TestPlugin { null }
        plugin.onEnable()
        plugin.onDisable()
    }
}
