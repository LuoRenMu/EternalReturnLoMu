package cn.luorenmu.service.entity

import cn.luorenmu.HTTP_SERVER_URL

/**
 * @author LoMu
 * Date 2026/5/24
 */
data class CharacterStats(
    val tier: String,
    val players: List<CharacterStatsPlayer>,
    val httpServer: String = HTTP_SERVER_URL,
) {
    data class CharacterStatsPlayer(
        val characterImgUrl: String,
        val weaponImgUrl: String,
        val tier: String,
        val winRate: String,
        val averageDamage: Int,
    )
}
