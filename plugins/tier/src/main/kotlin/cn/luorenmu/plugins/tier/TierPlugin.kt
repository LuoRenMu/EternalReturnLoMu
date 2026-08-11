package cn.luorenmu.plugins.tier

import cn.luorenmu.command.plugin.CommandPlugin

class TierPlugin : CommandPlugin {
    override val id = "tier"
    override val name = "段位数据"
    override val version = "1.0.0"
    override val commands = listOf(TierStatisticsNumberCommand(), EternalScoreCommand())
}
