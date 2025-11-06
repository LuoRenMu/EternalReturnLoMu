package cn.luorenmu.request.api.entity.response.user
import kotlinx.serialization.Serializable
/**
 *
 * @author LoMu
 * Date 2025/10/31 22:47
 */


@Serializable
data class UserStatsResponse(
    val code: Int,
    val message: String,
    val userStats: List<UserStats> = listOf(),
) {
    @Serializable
    data class UserStats(
        val seasonId: Int,
        val userNum: Long,
        val matchingMode: Int,
        val matchingTeamMode: Int,
        val mmr: Int,
        val nickname: String,
        val rank: Int,
        val rankSize: Int,
        val totalGames: Int,
        val totalWins: Int,
        val totalTeamKills: Int,
        val totalDeaths: Int,
        val escapeCount: Int,
        val rankPercent: Double,
        val averageRank: Double,
        val averageKills: Double,
        val averageAssistants: Double,
        val averageHunts: Double,
        val top1: Double,
        val top2: Double,
        val top3: Double,
        val top5: Double,
        val top7: Double,
        val characterStats: List<CharacterStats>,
    )

    @Serializable
    data class CharacterStats(
        val characterCode: Int,
        val totalGames: Int,
        val usages: Int,
        val maxKillings: Int,
        val top3: Int,
        val wins: Int,
        val top3Rate: Double,
        val averageRank: Double,
    )
}

