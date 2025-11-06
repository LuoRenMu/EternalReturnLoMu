package cn.luorenmu.request.api.entity.module

import cn.luorenmu.request.api.EternalReturnDakGGApiClient
import cn.luorenmu.request.api.entity.response.dakgg.DakGGCharacterImgType
import kotlinx.coroutines.runBlocking

/**
 *
 * @author LoMu
 * Date 2025/11/4 23:06
 */
enum class ImageResourcesType(val path: String, val fileType: String) {
    Weapon("/weapon/", ".png"),
    TierFull("/tier/full/", ".png"),
    TierRound("/tier/round/", ".png"),
    Character("/character/", ".png"),
    Item("/item/", ".png"),
    TacticalSkill("/tactical/skill/", ".png"),
    TraitSkill("/trait/skill/", ".png"),
    TraitSkillGroupPlaceholder("/trait/group/wilson", ".png"),
    TraitSkillGroup("/trait/group/", ".png");


    /**
     * 匹配核心技能
     */
    val traitSkillIdRegex = "[0-9]+".toRegex()
    fun getGeneralPath(name: String): String {
        var type = name
        if (this == TraitSkillGroup && traitSkillIdRegex.matches(name)) {
            val traitSkill = runBlocking { EternalReturnDakGGApiClient.getTraitSkills() }
            type = traitSkill.traitSkills.first { it.id == name.toLong() }.group
        }
        return "/resources/images${this.path}$type${this.fileType}"
    }

    companion object {

        const val TRAIT_SKILL_GROUP_PLACEHOLDER_WILSON_URL =
            "//cdn.dak.gg/er/images/common/img-placeholder-wilson-round.png"

        fun getCharacterPath(
            characterId: Int,
            skinId: Long,
            imageType: DakGGCharacterImgType,
        ): String {
            return "/resources/images${Character.path}${characterId}/${imageType.value}/${skinId}${Character.fileType}"
        }
    }
}