package cn.luorenmu.repository.entity

import kotlinx.serialization.Serializable

@Serializable
data class CommandUsageRecord(
    val id: Long,
    val commandName: String,
    val nickname: String?,
    val groupId: String?,
    val senderId: String?,
    val timestamp: String,
)
