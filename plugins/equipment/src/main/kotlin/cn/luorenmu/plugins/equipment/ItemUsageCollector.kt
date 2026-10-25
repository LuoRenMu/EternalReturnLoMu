package cn.luorenmu.plugins.equipment

import cn.luorenmu.common.util.PathUtils
import cn.luorenmu.request.api.entity.response.dakgg.DakGGCharactersResponse.DakGGCharacterById
import cn.luorenmu.request.api.entity.response.dakgg.DakGGCharacterImgType
import cn.luorenmu.request.api.entity.response.dakgg.DakGGWeaponResponse.Weapon
import cn.luorenmu.request.api.impl.EternalReturnDakGGApi
import kotlinx.coroutines.CancellationException
import java.nio.file.Files

data class ItemCharacterUsage(
    val characterId: Long,
    val characterName: String,
    val weaponName: String,
    val imageUrl: String,
    val usageRate: Double,
    val winRate: Double,
    val games: Long,
)

internal fun topItemCharacters(
    itemId: Long,
    response: EquipmentItemStatsResponse,
    characters: List<DakGGCharacterById>,
    weapons: List<Weapon>,
    limit: Int = 6,
): List<ItemCharacterUsage> {
    val characterById = characters.associateBy { it.id }
    val weaponById = weapons.associateBy { it.id.toLong() }
    return response.itemStatSnapshot.itemStats.firstOrNull { it.key == itemId }
        ?.characterWeaponStats.orEmpty()
        .asSequence()
        .filter { it.key.size >= 2 && it.count > 0 && it.count2 > 0 }
        .mapNotNull { stat ->
            val character = characterById[stat.key[0]] ?: return@mapNotNull null
            val weapon = weaponById[stat.key[1]] ?: return@mapNotNull null
            val skin = character.skins.firstOrNull()
            val portrait = skin?.let {
                PathUtils.resourcesPathResolve("images", "character", character.id.toString(),
                    "CharProfile", "${it.id}.png")
            }
            ItemCharacterUsage(
                character.id, character.name, weapon.name,
                portrait?.takeIf(Files::isRegularFile)?.toUri()?.toString()
                    ?: skin?.imageUrl
                    ?.replace(DakGGCharacterImgType.regex(), DakGGCharacterImgType.CharProfile.value)
                    .orEmpty(),
                stat.count.toDouble() / stat.count2 * 100,
                stat.win.toDouble() / stat.count * 100,
                stat.count,
            )
        }
        .filter { it.usageRate >= 1.0 }
        .sortedWith(compareByDescending<ItemCharacterUsage> { it.usageRate }.thenByDescending { it.games })
        .distinctBy { it.characterId }
        .take(limit)
        .toList()
}

internal suspend fun localizeItemPortraits(
    ranked: List<ItemCharacterUsage>,
    characters: List<DakGGCharacterById>,
): List<ItemCharacterUsage> = ranked.map { usage ->
    if (usage.imageUrl.startsWith("file:") || usage.imageUrl.isBlank()) usage
    else {
        val skinId = characters.firstOrNull { it.id == usage.characterId }?.skins?.firstOrNull()?.id
        if (skinId == null) usage else {
            val resource = EternalReturnDakGGApi.Image.DakGGImageUrlCharacter(
                usage.imageUrl, usage.characterId.toInt(), skinId, DakGGCharacterImgType.CharProfile
            )
            try {
                resource.callStream()
                usage.copy(imageUrl = resource.path.toUri().toString())
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                usage.copy(imageUrl = "")
            }
        }
    }
}
