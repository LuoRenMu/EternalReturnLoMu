package cn.luorenmu.request.api.entity.module

import cn.luorenmu.request.api.impl.EternalReturnDakGGApi
import kotlinx.coroutines.runBlocking

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
    CharacterProfile("/character/profile/", ".png"),

    /**
     * ID-SKIN_ID (CharResult)
     */
    CharacterResult("/character/result/", ".png"),

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
     * NULL
     * 未知图标占位符 (小威)
     */
    TraitSkillGroupPlaceholder("/trait/group/wilson", ".png"),

    /**
     * ID
     */
    TraitSkillGroup("/trait/group/", ".png");


    /**
     * 匹配核心技能
     */
    val traitSkillIdRegex = "[0-9]+".toRegex()

    companion object{
        // 小威下载地址
        const val TRAIT_SKILL_GROUP_PLACEHOLDER_WILSON_URL =
            "//cdn.dak.gg/er/images/common/img-placeholder-wilson-round.png"
    }
    fun getGeneralPath(name: String): String {
        var type = name
        if (this == TraitSkillGroup && traitSkillIdRegex.matches(name)) {
            val traitSkill = runBlocking { EternalReturnDakGGApi.Data.GetTraitSkills.execute() }
            type = traitSkill.traitSkills.first { it.id == name.toLong() }.group
        }
        return "/resources/images${this.path}$type${this.fileType}"
    }

    fun getCharacterPath(characterId: Int, skinId: Long): String {
        return "/resources/images${this.path}${characterId}/${skinId}${this.fileType}"
    }
}