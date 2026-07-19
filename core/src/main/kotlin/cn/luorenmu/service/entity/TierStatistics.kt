package cn.luorenmu.service.entity

import cn.luorenmu.HTTP_SERVER_URL
import cn.luorenmu.request.api.entity.response.dakgg.DakGGLeaderboardResponse
import java.text.SimpleDateFormat

/**
 *
 * @author LoMu
 * Date 2025/11/1 01:07
 */
data class TierStatistics(
    val season: String,
    val tierTypes: List<String>,
    val count: Map<String, Int>,
    val rate: Map<String, String>,
    val eternal: DakGGLeaderboardResponse.Cutoffs?,
    val demigod: DakGGLeaderboardResponse.Cutoffs?,
    val httpServer: String = HTTP_SERVER_URL,
    val date: String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(System.currentTimeMillis()),
) {
    override fun toString(): String {
        return """
            段位统计 {
            段位类型: ${tierTypes.joinToString(",")}
            段位人数: ${count.map { "${it.key}:${it.value}" }.joinToString(",")}
            段位占率: ${rate.map { "${it.key}:${it.value}" }.joinToString(",")}
            永恒段位: ${eternal?.mmr}
            半神段位: ${demigod?.mmr}}
        """.trimIndent()
    }
}