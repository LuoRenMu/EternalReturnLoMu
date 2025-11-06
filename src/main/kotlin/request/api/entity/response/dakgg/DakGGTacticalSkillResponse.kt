package cn.luorenmu.request.api.entity.response.dakgg

import cn.luorenmu.request.api.entity.response.dakgg.DakGGTacticalSkillResponse.TacticalSkill
import kotlinx.serialization.Serializable

/**
 *
 * @author LoMu
 * Date 2025/11/4 22:40
 */
@Serializable
data class DakGGTacticalSkillResponse(
    val tacticalSkills: List<TacticalSkill>,
) {
    @Serializable
    data class TacticalSkill(
        val id: Long = 0,
        val name: String = "",
        val tooltip: String = "",
        val imageUrl: String = "",
    )
}

fun DakGGTacticalSkillResponse.getTacticalSkill(id: Long): TacticalSkill {
    return this.tacticalSkills.first { it.id == id }
}