package cn.luorenmu.service.entity

import cn.luorenmu.HTTP_SERVER_URL

/**
 * @author LoMu
 * Date 2026/5/24
 */
data class CharacterStats(
    val totalGames: Int,
    val totalPlayers: Int,
    val tierName: String,
    val tier:String,
    val players: List<CharacterStatsPlayer>,
    val httpServer: String = HTTP_SERVER_URL,
) {
    data class CharacterStatsPlayer(
        val rank: Int,
        val characterImgUrl: String,
        val weaponImgUrl: String,
        val characterName: String,
        val tier: String,
        val rp: String,
        val playCount: Int,
        val winRate: String,
        val avgKill: String,
        val top3Rate: String,
        val pick: String,
        val avgRank: String,
        val avgDmg: String,
        val relativeWinRate: String,
    )
}
