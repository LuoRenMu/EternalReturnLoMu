package cn.luorenmu.request.api.entity.response.dakgg

import kotlinx.serialization.Serializable

/**
 * 角色技能数据（/v1/data/skills）。
 *
 * 每个角色拥有 Q/W/E/R/T 五个技能槽，通过 [DakGGSkill.characterId] + [DakGGSkill.slot] 唯一标识。
 *
 * @author LoMu
 * Date 2026/8/8
 */
@Serializable
data class DakGGSkillsResponse(
    val skills: List<DakGGSkill> = emptyList(),
) {
    @Serializable
    data class DakGGSkill(
        val id: Long = 0,
        val name: String = "",
        val tooltip: String = "",
        val characterId: Long = 0,
        val maxLevel: Int = 0,
        val slot: String = "",
        val imageUrl: String = "",
    )

    /** 按角色 id + 技能槽（"Q"/"W"/"E"/"R"/"T"）查技能，未匹配返回 null。 */
    fun findSkill(characterId: Long, slot: String): DakGGSkill? =
        skills.firstOrNull { it.characterId == characterId && it.slot == slot }
}
