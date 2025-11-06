package cn.luorenmu.request.api.entity.response.data

import cn.luorenmu.request.entity.LocalDateTimeSerializer
import kotlinx.serialization.Serializable
import java.time.LocalDateTime

/**
 *
 * @author LoMu
 * Date 2025/10/25 16:11
 */
@Serializable
data class GameDataSeasonResponse(
    val seasonID: Int,
    val seasonName: String,
    @Serializable(with = LocalDateTimeSerializer::class)
    val seasonStart: LocalDateTime,
    @Serializable(with = LocalDateTimeSerializer::class)
    val seasonEnd: LocalDateTime,
    val isCurrent: Int,
)

