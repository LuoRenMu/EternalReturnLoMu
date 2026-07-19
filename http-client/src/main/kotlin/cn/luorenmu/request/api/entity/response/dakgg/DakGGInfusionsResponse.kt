package cn.luorenmu.request.api.entity.response.dakgg

import cn.luorenmu.request.api.impl.EternalReturnDakGGApi
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable

/**
 *
 * @author LoMu
 * 钴协议灌注数据：id 为 boughtInfusion 的 key，productId 指向具体物品/天赋/战术技能。
 */
@Serializable
data class DakGGInfusionsResponse(
    val infusions: List<Infusion> = listOf(),
) {
    @Serializable
    data class Infusion(
        val id: Long = 0,
        val productType: String = "",
        val productId: Long = 0,
    )

    fun getInfusionById(id: Long): Infusion? {
        return this.infusions.firstOrNull { it.id == id } ?: runBlocking {
            EternalReturnDakGGApi.Data.GetInfusions.refresh()
            EternalReturnDakGGApi.Data.GetInfusions.execute().infusions.firstOrNull { it.id == id }
        }
    }
}