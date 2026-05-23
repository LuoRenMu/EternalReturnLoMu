package cn.luorenmu.request.api.entity.response.dakgg

import cn.luorenmu.request.api.impl.EternalReturnDakGGApi
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable

/**
 *
 * @author LoMu
 * Date 2025/11/4 22:26
 */
@Serializable
data class DakGGItemsResponse(
    val items: List<Item>,
) {
    @Serializable
    data class Item(
        val id: Long = 0,
        val name: String = "",
        val tooltip: String = "",
        val imageUrl: String = "",
        val type: String = "",
        val miscItemType: String? = null,
        val grade: String = "",
        val spawnAreas: List<Long>? = null,
        val weaponType: String? = null,
        val makeMaterial1: Long? = null,
        val makeMaterial2: Long? = null,
        val makeMaterials: List<Long>? = null,
        val armorType: String? = null,
        val consumableType: String? = null,
        val consumableTag: String? = null,
        val specialItemType: String? = null,
    )
    fun getItemById(id: Long): Item {
        return this.items.firstOrNull { it.id == id }
            ?: runBlocking {
                EternalReturnDakGGApi.Data.GetItems.refresh()
                EternalReturnDakGGApi.Data.GetItems.execute().items.first { it.id == id }
            }
    }
}

