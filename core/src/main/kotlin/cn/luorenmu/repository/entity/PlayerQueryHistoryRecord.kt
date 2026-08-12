package cn.luorenmu.repository.entity

import java.time.LocalDateTime

data class PlayerQueryHistoryRecord(
    val nickname: String,
    val queryCount: Long,
    val firstQueryAt: LocalDateTime,
    val lastQueryAt: LocalDateTime,
)
