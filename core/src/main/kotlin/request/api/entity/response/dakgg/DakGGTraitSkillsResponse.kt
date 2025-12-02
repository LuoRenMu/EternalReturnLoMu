package cn.luorenmu.request.api.entity.response.dakgg

import kotlinx.serialization.Serializable

/**
 *
 * @author LoMu
 * Date 2025/11/4 22:39
 */
@Serializable
data class DakGGTraitSkillsResponse(
    val traitSkillGroups: List<TraitSkillGroup> = listOf(),
    val traitSkills: List<TraitSkill> = listOf(),
) {

    @Serializable
    data class TraitSkillGroup(
        var key: String = "",
        val name: String = "",
        val tooltip: String = "",
        val imageUrl: String = "",
    )
    @Serializable
    data class TraitSkill(
        val id: Long = 0,
        val name: String = "",
        val tooltip: String = "",
        val group: String = "",
        val type: String = "",
        val imageUrl: String = "",
        val active: Boolean = false,
    )
}
