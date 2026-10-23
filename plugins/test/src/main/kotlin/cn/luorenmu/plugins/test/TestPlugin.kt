package cn.luorenmu.plugins.test

import cn.luorenmu.command.plugin.CommandPlugin
import love.forte.simbot.event.EventListenerRegistrationHandle
import cn.luorenmu.common.util.DatabaseManager
import cn.luorenmu.plugins.qqmedia.QQProactiveGroupSender
import cn.luorenmu.plugins.qqmedia.SimbotQQRuntime
import love.forte.simbot.message.toText
import org.koin.java.KoinJavaComponent.inject

class TestPlugin(
    private val registerCallback: () -> EventListenerRegistrationHandle? = { null },
) : CommandPlugin {
    override val id = "test"
    override val name = "QGBOT 测试"
    override val version = "1.7.0"
    private val scheduler = ProactiveTestScheduler()
    private val database: DatabaseManager by inject(DatabaseManager::class.java)
    private val monitorDelegate = lazy {
        val source = BiliLiveSource()
        BiliLiveMonitor(BiliLiveRepository { block -> database.useConnection(block) }, source::fetch,
            { group, text -> QQProactiveGroupSender.send(SimbotQQRuntime.requireBot(), group, text.toText()) })
    }
    private val monitor by monitorDelegate
    override val commands = listOf<cn.luorenmu.command.CommandEvent>(ProactiveTestCommand(scheduler, live = { monitor }))
    private var callbackRegistration: EventListenerRegistrationHandle? = null

    override fun onEnable() {
        scheduler.start()
        monitor.start()
        if (callbackRegistration == null) callbackRegistration = registerCallback()
    }

    override fun onDisable() {
        scheduler.stop()
        if (monitorDelegate.isInitialized()) monitor.stop()
        callbackRegistration?.dispose()
        callbackRegistration = null
    }
}
