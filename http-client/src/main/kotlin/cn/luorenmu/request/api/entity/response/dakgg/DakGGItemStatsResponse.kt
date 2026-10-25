package cn.luorenmu.request.api.entity.response.dakgg

import kotlinx.serialization.Serializable

@Serializable
data class DakGGItemStatsResponse(
    val meta: Meta = Meta(),
    val itemStatSnapshot: Snapshot = Snapshot(),
) {
    @Serializable
    data class Meta(val patch: Int = 0, val dt: Int = 7, val tier: String = "", val updatedAt: Long = 0)

    @Serializable
    data class Snapshot(val itemStats: List<ItemStat> = emptyList())

    @Serializable
    data class ItemStat(
        val key: Long = 0,
        val characterWeaponStats: List<CharacterWeaponStat> = emptyList(),
    )

    @Serializable
    data class CharacterWeaponStat(
        val key: List<Long> = emptyList(),
        val count: Long = 0,
        val count2: Long = 0,
        val win: Long = 0,
    )
}
