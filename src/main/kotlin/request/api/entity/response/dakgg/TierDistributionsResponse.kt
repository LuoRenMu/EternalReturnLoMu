package cn.luorenmu.request.api.entity.response.dakgg

import kotlinx.serialization.Serializable

/**
 *
 * @author LoMu
 * Date 2025/11/1 00:25
 */
@Serializable
data class TierDistributionsResponse(
    val distributions: ArrayList<Distributions>,
    val updatedAt: Long,
) {
    @Serializable
    data class Distributions(
        val count: Int,
        val rate: Double,
        val tierGrade: Int,
        val tierImageUrl: String,
        val tierType: Int,
    )
}