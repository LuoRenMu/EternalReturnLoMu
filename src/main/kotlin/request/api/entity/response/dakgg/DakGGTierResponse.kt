package cn.luorenmu.request.api.entity.response.dakgg

import kotlinx.serialization.Serializable

/**
 *
 * @author LoMu
 * Date 2025/11/5 13:10
 */
@Serializable
data class DakGGTiersResponse(
    val tiers: ArrayList<EternalReturnTier> = arrayListOf(),
) {
    @Serializable
    data class EternalReturnTier(
        val id: Int = 0,
        val key: String = "",
        val name: String = "",
        val imageUrl: String = "",
        val iconUrl: String = "",
    )
}
