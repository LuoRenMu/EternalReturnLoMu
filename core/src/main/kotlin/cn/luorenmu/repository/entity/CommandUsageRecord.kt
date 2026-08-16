package cn.luorenmu.repository.entity

import kotlinx.serialization.Serializable

@Serializable
/**
 *
 * @author LoMu
 * Date 2026/8/16 15:30
 */
data class CommandUsageRecord(
    val id: Long,
    val commandName: String,
    val nickname: String?,
    val groupId: String?,
    val senderId: String?,
    val timestamp: String,
)
