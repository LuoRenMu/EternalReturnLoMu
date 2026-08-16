package cn.luorenmu.repository.entity

import kotlinx.serialization.Serializable

@Serializable
/**
 *
 * @author LoMu
 * Date 2026/8/16 15:30
 */
data class ExceptionLogRecord(
    val id: Long,
    val source: String,
    val exceptionType: String,
    val message: String,
    val context: String,
    val stackTrace: String,
    val occurredAt: String,
)
