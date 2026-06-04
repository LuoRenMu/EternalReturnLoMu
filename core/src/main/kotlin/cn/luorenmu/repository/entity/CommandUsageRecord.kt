package cn.luorenmu.repository.entity

import java.time.LocalDateTime

/**
 * 命令使用记录实体
 *
 * @author LoMu
 * Date 2026/5/1 18:36
 */
data class CommandUsageRecord(
    val id: Long = 0,
    val commandName: String,
    val nickname: String? = null,
    val timestamp: LocalDateTime,
)
