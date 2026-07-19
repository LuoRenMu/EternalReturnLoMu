package cn.luorenmu.request.api.entity.response.dakgg

import kotlinx.serialization.Serializable

/**
 *
 * @author LoMu
 * Date 2025/11/1 00:29
 */
@Serializable
data class DakGGLeaderboardResponse(
    val cutoffs: ArrayList<Cutoffs>,
    val leaderboards: ArrayList<LeaderboardPlayer>,
    val playerTierByUserNum: HashMap<Int, PlayerTierByUserNum>,
    val tierDistributionDtos: ArrayList<TierDistributionDtos>,
    val totalLeaderBoardCount: Int,
    val updatedAt: Long,
) {
    @Serializable
    data class Cutoffs(
        val mmr: Int = 0,
        val teamModeId: Int = 0,
        val tierType: Int = 0,
    )

    @Serializable
    data class LeaderboardPlayer(
        val avgPlacement: Double = 0.0,
        val avgPlayerKill: Double = 0.0,
        val characterIds: ArrayList<Int>? = null,
        val mmr: Int = 0,
        val mostCharacters: ArrayList<CharacterPickRate> = arrayListOf(),
        val nickname: String = "螺母",
        val playCount: Int = 0,
        val rank: Int = 0,
        val rankDiff: Int = 0,
        val top3Rate: Double = 0.0,
        val userNum: Long = 0,
        val winRate: Double = 0.0,
    ) {
        @Serializable
        data class CharacterPickRate(
            val characterId: Int,
            val pickRate: Double = 0.0,
        )
    }

    @Serializable
    data class PlayerTierByUserNum(
        val imageUrl: String,
        val lp: Int,
        val mmr: Int,
        // 段位名
        val name: String,
        val seasonId: Int,
        val tierGrade: Int,
        val tierType: Int,
    )

    // 段位图 >= 无暇
    @Serializable
    data class TierDistributionDtos(
        val count: Int = 0,
        val rate: Double = 0.0,
        val tierGrade: Int = 0,
        val tierImageUrl: String = "",
        val tierType: Int = 0,
    )

}

/**
 * 解析 cutoffs 返回 (eternal, demigod) 对，数据不存在时返回 null。
 * size=1 两者相同，size=2 时 [1]=eternal [0]=demigod，其他返回 null。
 */
fun ArrayList<DakGGLeaderboardResponse.Cutoffs>.resolveCutoffs(): Pair<DakGGLeaderboardResponse.Cutoffs, DakGGLeaderboardResponse.Cutoffs>? =
    when (size) {
        1 -> get(0) to get(0)
        2 -> get(1) to get(0)
        else -> null
    }