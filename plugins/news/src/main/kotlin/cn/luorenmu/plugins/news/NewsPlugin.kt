package cn.luorenmu.plugins.news

import cn.luorenmu.command.plugin.CommandPlugin

/**
 *
 * @author LoMu
 * Date 2026/8/16 15:30
 */
class NewsPlugin : CommandPlugin {
    override val id = "news"
    override val name = "活动与兑换码"
    override val version = "1.0.0"
    override val commands = listOf(RedemptionCodeCommand(), RedemptionCodeActivityCommand())
}
