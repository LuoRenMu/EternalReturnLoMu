package cn.luorenmu.plugins.news

import cn.luorenmu.command.plugin.CommandPlugin

/**
 *
 * @author LoMu
 * Date 2026/8/16 15:30
 */
class NewsPlugin : CommandPlugin {
    override val id = "news"
    override val name = "公告、活动与兑换码"
    override val version = "1.3.1"
    private val pushWorker = RedemptionPushWorker()
    override fun onEnable() = pushWorker.start()
    override fun onDisable() = pushWorker.stop()
    override val commands = listOf(RedemptionCodeCommand(), RedemptionCodeActivityCommand())
}
