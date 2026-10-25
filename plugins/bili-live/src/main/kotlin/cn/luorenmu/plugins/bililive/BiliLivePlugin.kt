package cn.luorenmu.plugins.bililive

import cn.luorenmu.common.util.DatabaseManager
import cn.luorenmu.Adapter
import cn.luorenmu.currentAdapter
import cn.luorenmu.command.plugin.CommandPlugin
import cn.luorenmu.command.CommandEvent
import cn.luorenmu.plugins.qqmedia.BackgroundPolls
import cn.luorenmu.plugins.qqmedia.QQProactiveGroupSender
import cn.luorenmu.plugins.qqmedia.SimbotQQRuntime
import org.koin.java.KoinJavaComponent.inject

class BiliLivePlugin : CommandPlugin {
    override val id = "bili-live"
    override val name = "B站直播通知"
    override val version = "1.2.1"
    private val database: DatabaseManager by inject(DatabaseManager::class.java)
    private val access = LiveNotificationAccess()
    private val monitorDelegate = lazy {
        val source = BiliLiveSource()
        val messages = BiliLiveMessages()
        BiliLiveMonitor(BiliLiveRepository { block -> database.useConnection(block) }, source::fetch,
            { group, notice -> QQProactiveGroupSender.send(SimbotQQRuntime.requireBot(), group, messages.build(notice)) },
            permission = access::enabled,
            poll = { id -> BackgroundPolls.query { source.fetch(id) } })
    }
    override val commands: List<CommandEvent> = listOf(BiliLiveCommand({ monitorDelegate.value }, access::enabled))

    override fun onEnable() {
        if (currentAdapter == Adapter.QG_BOT) monitorDelegate.value.start()
    }
    override fun onDisable() {
        if (monitorDelegate.isInitialized()) monitorDelegate.value.stop()
    }
}
