package cn.luorenmu.plugins.match

/** Presentation data, not the upstream API DTO. Unknown values remain unknown. */
data class MatchDetailData(
    val gameId: String,
    val mode: String,
    val server: String,
    val startedAt: String,
    val durationSeconds: Long?,
    val version: String,
    val teams: List<MatchTeam>,
    val focusedPlayerId: Long? = null,
    val designSample: Boolean = false,
) {
    init {
        require(teams.map { it.number }.distinct().size == teams.size) { "Duplicate team number" }
        val ids = teams.flatMap { it.players }.map { it.id }
        require(ids.distinct().size == ids.size) { "Duplicate player id" }
    }
}

data class MatchTeam(val number: Int, val rank: Int?, val kills: Int?, val players: List<MatchPlayer>)

data class MatchPlayer(
    val id: Long,
    val nickname: String,
    val character: String,
    val portrait: String? = null,
    val kills: Int? = null,
    val deaths: Int? = null,
    val assists: Int? = null,
    val damage: Long? = null,
    val taken: Long? = null,
    val survivalSeconds: Long? = null,
    val rp: Int? = null,
    val rpChange: Int? = null,
    val healing: Long? = null,
    val vision: Long? = null,
    val credits: Long? = null,
    val equipment: List<String?> = emptyList(),
    val equipmentBackgrounds: List<String?> = emptyList(),
    val tierName: String? = null,
    val tierIcon: String? = null,
    val weaponIcon: String? = null,
    val traitSkillIcon: String? = null,
    val tacticalSkillIcon: String? = null,
)
