package cn.luorenmu.request.api.entity.response.data

import kotlinx.serialization.Serializable

/**
 *
 * @author LoMu
 * Date 2025/10/25 16:10
 */
@Serializable
data class BaseGameDataResponse<T>(
    val code: Int,
    val message: String,
    val description: String?,
    val data: T,
)

