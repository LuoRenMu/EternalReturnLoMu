package cn.luorenmu.repository.entity

import java.time.LocalDateTime

/**
 *
 * @author LoMu
 * Date 2026/8/16 15:30
 */
data class PlayerQueryHistoryRecord(
    val nickname: String,
    val queryCount: Long,
    val firstQueryAt: LocalDateTime,
    val lastQueryAt: LocalDateTime,
)
