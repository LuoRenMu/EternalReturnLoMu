package cn.luorenmu.request.api.entity.response.dakgg

import cn.luorenmu.request.api.impl.EternalReturnDakGGApi
import kotlinx.coroutines.runBlocking
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

    /**
     * 获取 "无段位"信息
     */
    fun getUnRank(): EternalReturnTier {
        return tiers.firstOrNull { it.id == 0 }
            ?: runBlocking {
                EternalReturnDakGGApi.Data.GetTiers.refresh()
                EternalReturnDakGGApi.Data.GetTiers.execute().tiers.first { it.id == 0 }
            }
    }

    fun getTierById(id: Int): EternalReturnTier {
        return tiers.firstOrNull { it.id == id }
            ?: runBlocking {
                EternalReturnDakGGApi.Data.GetTiers.refresh()
                EternalReturnDakGGApi.Data.GetTiers.execute().tiers.first { it.id == id }
            }
    }
}
