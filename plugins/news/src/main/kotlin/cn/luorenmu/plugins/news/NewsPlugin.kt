package cn.luorenmu.plugins.news

import cn.luorenmu.command.plugin.CommandPlugin

class NewsPlugin : CommandPlugin {
    override val id = "news"
    override val name = "活动与兑换码"
    override val version = "1.0.0"
    override val commands = listOf(RedemptionCodeCommand(), RedemptionCodeActivityCommand())
}
