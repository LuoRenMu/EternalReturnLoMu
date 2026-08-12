package cn.luorenmu.plugins.querystatistics

import cn.luorenmu.repository.entity.PlayerQueryHistoryRecord

data class QueryStatisticsData(
    val senderName: String,
    val history: List<PlayerQueryHistoryRecord>,
)
