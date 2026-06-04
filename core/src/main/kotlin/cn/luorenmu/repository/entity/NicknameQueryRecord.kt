package cn.luorenmu.repository.entity

import java.time.LocalDateTime

/**
 * 昵称查询记录实体
 *
 * @author LoMu
 * Date 2026/5/1 18:37
 */
data class NicknameQueryRecord(
    val id: Long = 0,
    val nickname: String,
    val queryCount: Long = 0L,
    val firstQueryAt: LocalDateTime,
    val lastQueryAt: LocalDateTime,
)
