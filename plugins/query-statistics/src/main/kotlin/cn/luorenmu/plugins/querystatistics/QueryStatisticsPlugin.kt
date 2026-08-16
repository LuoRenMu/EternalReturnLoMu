package cn.luorenmu.plugins.querystatistics

import cn.luorenmu.command.plugin.CommandPlugin

/**
 *
 * @author LoMu
 * Date 2026/8/16 15:30
 */
class QueryStatisticsPlugin : CommandPlugin {
    override val id = "query-statistics"
    override val name = "查询统计"
    override val version = "1.0.0"
    override val commands = listOf(QueryStatisticsCommand())
}
