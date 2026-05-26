package cn.luorenmu.request.api.entity.response.dakgg

import cn.luorenmu.request.api.impl.EternalReturnDakGGApi
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable

/**
 *
 * @author LoMu
 * Date 2025/11/4 22:37
 */
@Serializable
data class DakGGWeaponResponse(
    val masteries: List<Weapon> = listOf(),
) {
    @Serializable
    data class Weapon(
        val id: Int = 0,
        val key: String = "",
        val name: String = "",
        val iconUrl: String = "",
    )
    fun getWeaponById(id: Int): Weapon {
        return this.masteries.firstOrNull { it.id == id }
            ?: runBlocking {
                EternalReturnDakGGApi.Data.GetWeapons.refresh()
                EternalReturnDakGGApi.Data.GetWeapons.execute().masteries.first { it.id == id }
            }
    }

}
