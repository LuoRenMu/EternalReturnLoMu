package cn.luorenmu.plugins.character

import cn.luorenmu.request.api.entity.module.ImageResourcesType

private val CHARACTER_TIER_GRADES = setOf("S", "A", "B", "C", "D")

internal fun characterTierIconUrl(tier: String): String? {
    val grade = tier.trim().uppercase()
    return grade.takeIf { it in CHARACTER_TIER_GRADES }
        ?.let { ImageResourcesType.CharacterTier.getGeneralPath("character-tier-$it") }
}
