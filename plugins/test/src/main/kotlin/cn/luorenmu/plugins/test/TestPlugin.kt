package cn.luorenmu.plugins.test

import cn.luorenmu.command.plugin.CommandPlugin
import love.forte.simbot.event.EventListenerRegistrationHandle

class TestPlugin(
    private val registerCallback: () -> EventListenerRegistrationHandle? = { null },
) : CommandPlugin {
    override val id = "test"
    override val name = "QGBOT 测试"
    override val version = "1.9.0"
    private val scheduler = ProactiveTestScheduler()
    override val commands = listOf<cn.luorenmu.command.CommandEvent>(ProactiveTestCommand(scheduler))
    private var callbackRegistration: EventListenerRegistrationHandle? = null

    override fun onEnable() {
        scheduler.start()
        if (callbackRegistration == null) callbackRegistration = registerCallback()
    }

    override fun onDisable() {
        scheduler.stop()
        callbackRegistration?.dispose()
        callbackRegistration = null
    }
}
