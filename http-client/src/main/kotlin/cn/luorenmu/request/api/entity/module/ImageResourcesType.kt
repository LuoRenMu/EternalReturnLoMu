package cn.luorenmu.request.api.entity.module

import cn.luorenmu.request.api.entity.response.dakgg.DakGGCharacterImgType
import cn.luorenmu.request.api.impl.EternalReturnDakGGApi
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap

/**
 *
 * @author LoMu
 * Date 2025/11/4 23:06
 */
enum class ImageResourcesType(val path: String, val fileType: String) {
    /**
     * ID
     */
    Weapon("/weapon/", ".png"),

    /**
     * ID
     */
    TierFull("/tier/full/", ".png"),

    /**
     * ID
     */
    TierRound("/tier/round/", ".png"),

    /**
     * ID-SKIN_ID (CharProfile)
     */
    Character("/character/", ".png"),

    /**
     * character-tier-{S/A/B/C/D}
     */
    CharacterTier("/character/tier/", ".svg"),

    /**
     * ID
     */
    Item("/item/", ".png"),

    /**
     * ID
     */
    ItemBg("/item/bg/", ".svg"),

    /**
     * ID
     */
    TacticalSkill("/tactical/skill/", ".png"),

    /**
     * ID
     */
    TraitSkill("/trait/skill/", ".png"),

    /**
     * ID
     * 角色技能（Q/W/E/R/T）图标
     */
    Skill("/skill/", ".png"),

    /**
     * NULL
     * 未知图标占位符 (小威)
     */
    TraitSkillGroupPlaceholder("/trait/group/wilson", ".png"),

    /**
     * ID
     */
    TraitSkillGroup("/trait/group/", ".png"),

    /**
     * bg-landing-search-v{bannerId}
     */
    Banner("/bg/", ".jpg");


    /**
     * 匹配核心技能
     */
    val traitSkillIdRegex = "[0-9]+".toRegex()

    /**
     * 缓存 traitSkillId → group 的映射，避免反复调用 runBlocking + API。
     */
    private val traitGroupCache = ConcurrentHashMap<String, String>()

    companion object {
        // 小威下载地址
        const val TRAIT_SKILL_GROUP_PLACEHOLDER_WILSON_URL =
            "//cdn.dak.gg/er/images/common/img-placeholder-wilson-round.png"

        fun bannerNameForSeason(seasonId: Int): String {
            val bannerId = (seasonId - 29).coerceAtLeast(1)
            return "bg-landing-search-v$bannerId"
        }

        fun bannerPathForSeason(seasonId: Int): String =
            Banner.getGeneralPath(bannerNameForSeason(seasonId))

        fun resolveBannerSeasonId(gameSeasonIds: Iterable<Long>, fallbackSeasonId: Int): Int =
            gameSeasonIds.maxOrNull()?.toInt()?.takeIf { it > 0 }
                ?: fallbackSeasonId.takeIf { it > 0 }
                ?: 39
    }

    fun getGeneralPath(name: String): String {
        var type = name
        if (this == TraitSkillGroup && traitSkillIdRegex.matches(name)) {
            type = traitGroupCache.getOrPut(name) {
                val traitSkill = runBlocking { EternalReturnDakGGApi.Data.GetTraitSkills.execute() }
                traitSkill.getTraitSkillById(name.toLong()).group
            }
        }
        return "/resources/images${this.path}$type${this.fileType}"
    }

    fun getCharacterPath(characterId: Int, skinId: Long, imgType: DakGGCharacterImgType): String {
        return "/resources/images${this.path}${characterId}/${imgType.value}/${skinId}${this.fileType}"
    }
}
