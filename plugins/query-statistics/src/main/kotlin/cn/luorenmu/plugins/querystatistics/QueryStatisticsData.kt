package cn.luorenmu.plugins.querystatistics

import cn.luorenmu.repository.entity.PlayerQueryHistoryRecord

/**
 *
 * @author LoMu
 * Date 2026/8/16 15:30
 */
data class QueryStatisticsData(
    val senderName: String,
    val history: List<PlayerQueryHistoryRecord>,
)
