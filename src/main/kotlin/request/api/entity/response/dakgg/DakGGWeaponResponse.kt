package cn.luorenmu.request.api.entity.response.dakgg

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
}

fun DakGGWeaponResponse.getWeaponById(id: Int): DakGGWeaponResponse.Weapon {
    return this.masteries.first { it.id == id }
}
