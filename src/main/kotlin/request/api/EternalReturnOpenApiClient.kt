package cn.luorenmu.request.api

import cn.luorenmu.exception.NotFoundNickNameException
import cn.luorenmu.request.RequestManager
import cn.luorenmu.request.api.entity.response.dakgg.convert
import cn.luorenmu.request.api.entity.response.data.BaseGameDataResponse
import cn.luorenmu.request.api.entity.response.data.GameDataSeasonResponse
import cn.luorenmu.request.api.entity.response.game.BattleUserGamesResponse
import cn.luorenmu.request.api.entity.response.user.UserNickNameResponse
import cn.luorenmu.request.api.entity.response.user.UserStatsResponse
import cn.luorenmu.request.entity.module.MatchingMode
import cn.luorenmu.request.entity.module.MatchingTeamMode
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.call.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import love.forte.simbot.message.toText
import java.time.LocalDateTime

/**
 *
 * @author LoMu
 * Date 2025/10/25 19:07
 */
object EternalReturnOpenApiClient {
    private val log = KotlinLogging.logger {  }
    suspend fun getUserNumByUserNickName(username: String): UserNickNameResponse {
        val resp = RequestManager.call(EternalReturnOpenApi.User.GetIdByNickName(username))
        val body = resp.body<JsonObject>()
        if (resp.status.value == 404 || body.jsonObject["code"]!!.jsonPrimitive.content == "404") {
            throw NotFoundNickNameException("没有找到的用户名称  (${username[0]}***)  请检查名称".toText())
        }
        val obj = resp.body<UserNickNameResponse>()
        return obj
    }

    suspend fun getGamesByUserNum(userNum: Long): BattleUserGamesResponse {
        val resp =
            RequestManager.call(EternalReturnOpenApi.Game.GetGamesByUserId(userNum))
        return resp.body<BattleUserGamesResponse>()
    }

    suspend fun getDataCurrentSeason(): GameDataSeasonResponse {
        val resp =
            RequestManager.call(EternalReturnOpenApi.Data.GetGameDataBySeason)
        val seasons = resp.body<BaseGameDataResponse<MutableList<GameDataSeasonResponse>>>()
        val season = seasons.data.first { it.isCurrent == 1 }
        // 官方API存在数据落后性，从DAK.GG中获取
        if (season.seasonEnd.isBefore(LocalDateTime.now())) {
            val dakGGCurrentSeason = EternalReturnDakGGApiClient.getDataCurrentSeason()
            if (dakGGCurrentSeason.id == season.seasonID) {
                return season
            }
            return dakGGCurrentSeason.convert()
        }
        return season
    }

    suspend fun getUserStats(userNum: Long, seasonId: Int, matchingMode: MatchingMode): UserStatsResponse {
        val api = EternalReturnOpenApi.User.GetUserStats(userNum, seasonId, matchingMode)
        val resp = RequestManager.call(api)
        return resp.body<UserStatsResponse>()
    }


    suspend fun getGameByGameId(gameId: Long): BattleUserGamesResponse {
        val resp =
            RequestManager.call(EternalReturnOpenApi.Game.GetGameByGameId(gameId))
        return resp.body<BattleUserGamesResponse>()
    }

    suspend fun getGlobalRank(seasonId: Int, matchingTeamMode: MatchingTeamMode) {
        val resp =
            RequestManager.call(
                EternalReturnOpenApi.Rank.GetGlobalRank(seasonId, matchingTeamMode)
            )
        TODO("return global rank obj")
    }


}