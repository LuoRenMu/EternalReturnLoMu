package cn.luorenmu.repository.entity

import kotlinx.serialization.Serializable

@Serializable
data class ExceptionLogRecord(
    val id: Long,
    val source: String,
    val exceptionType: String,
    val message: String,
    val context: String,
    val stackTrace: String,
    val occurredAt: String,
)
