package cn.luorenmu.plugins.character

import cn.luorenmu.request.api.entity.module.ImageResourcesType
import cn.luorenmu.nutdraw.css.CssStyle
import cn.luorenmu.nutdraw.css.ObjectFit
import cn.luorenmu.nutdraw.css.px
import cn.luorenmu.nutdraw.dom.ElementBuilder

/**
 *
 * @author LoMu
 * Date 2026/8/16 15:30
 */
private val CHARACTER_TIER_GRADES = setOf("S", "A", "B", "C", "D")

internal fun characterTierIconUrl(tier: String): String? {
    val grade = tier.trim().uppercase()
    return grade.takeIf { it in CHARACTER_TIER_GRADES }
        ?.let { ImageResourcesType.CharacterTier.getGeneralPath("character-tier-$it") }
}

/** 将评级作为元素自身的居中 COVER 背景绘制，确保 SVG 不会越过元素边界。 */
internal fun ElementBuilder.characterTierIcon(
    source: String?,
    size: Float,
    id: String,
) {
    element(
        CssStyle(
            width = px(size),
            height = px(size),
            backgroundImage = source,
            objectFit = ObjectFit.COVER,
        ),
        id = id,
    )
}
