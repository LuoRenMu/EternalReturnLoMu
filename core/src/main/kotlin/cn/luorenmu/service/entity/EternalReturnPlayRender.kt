package cn.luorenmu.service.entity

import cn.luorenmu.HTTP_SERVER_URL
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * @author LoMu
 * Date 2025.03.29 15:08
 */
data class EternalReturnPlayRender(
    val nickName: String = "螺母",
    val level: Int = 1,
    val data: EternalReturnPlayerData,
    val profileImageUrl: String? = null,
    val recentPlayers: List<EternalReturnPlayerRecentPlay>,
    val characterUseStats: List<EternalReturnCharacterUseStats>,
    var summary: EternalReturnSummary?,
    val mmrStats: EternalReturnPlayerMMRStats? = null,
    var matches: List<EternalReturnPlayerMatchData> = mutableListOf(),
    val season: String,
    val httpServer: String = HTTP_SERVER_URL,
    val mode :String = "排位"
) {

    data class EternalReturnSummary(
        val count: Int,
        val avgRank: String,
        val wins: String,
        val avgTk: String,
        val ranks: List<Int>,
        val avgDmg: String
    )

    data class EternalReturnPlayerMMRStats(
        val mmrDate: List<String>,
        val mmr: List<Int>,
    ) {
        val mmrDateJson = Json.encodeToString(mmrDate)
        val mmrJson: String = Json.encodeToString(mmr)
    }

    data class EternalReturnCharacterUseStats(
        val imgUrl: String,
        val characterName: String,
        val characterPlay: Int,
        val winRate: String,
        val getRP: Int,
        val avgRank: String,
        val avgDmg: Int,
    )

    data class EternalReturnPlayerData(
        var rp: String = "段位鉴定中.",
        var rpName: String = "",
        var tierImageUrl: String = "",
        var play: Int = 0,
        var avgTk: String = "-",
        var avgKill: String = "-",
        var avgRank: String = "-",
        var avgAssists: String = "-",
        var avgDmg: String = "-",
        var top1: String = "-",
        var top2: String = "-",
        var top3: String = "-",
    )

    data class EternalReturnPlayerRecentPlay(
        var imageWrapperUrl: String = "",
        var plays: Int = 1,
        var winRate: String = "0.00%",
        var avgRank: String = "0.00%",
        var nickname: String = "",
        var characterName: String = "",
    )


    data class EternalReturnPlayerMatchData(
        val serverName: String = "",
        val nickName: String = "螺母",
        val characterName: String = "螺母",
        val rank: Int = 8,
        val type: String = "排位",
        val dateHour: String = "25:00",
        val dateMonth: String = "13月13日",
        val characterAvatarUrl: String = "",
        val weaponUrl: String = "",
        val traitSkillGroupUrl: String = "",
        val tacticalSkillUrl: String = "",
        val traitSkillUrl: String = "",
        val kill: Int = 0,
        val assist: Int = 0,
        val kda: Double = 0.00,
        val dmg: Long = 0,
        val tk: Int = 0,
        val rpChange: Int = 0,
        val rp: Int = 0,
        val rpSvgUrl: String = "",
        val routeId: String = "Private",
        val equips: MutableList<EternalReturnEquip> = mutableListOf(),
        val gameId: String = "0",
        val version: String = "",
        val teamMates: List<EternalReturnTeammate>? = null,
    ) {
        data class EternalReturnTeammate(
            var nickName: String = "",
            var avatarUrl: String = "",
            var rp: String = "0",
            var rpImageUrl: String = "",
            var tk: Int = 0,
            var kill: Int = 0,
            var assist: Int = 0,
            var dmg: Int = 0,
            var weaponUrl: String = "",
            var skillUrl: String = "",
            var traitSkillGroupUrl: String = "",
            var traitSkillUrl: String = "",
            var equips: MutableList<EternalReturnEquip> = mutableListOf(),
        )
    }

}