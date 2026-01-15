package cn.luorenmu.request.api

import cn.luorenmu.exception.NotFoundNickNameException
import cn.luorenmu.request.RequestManager
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




    suspend fun getUserStats(userNum: String, seasonId: Int, matchingMode: MatchingMode): UserStatsResponse {
        val api = EternalReturnOpenApi.User.GetUserStats(userNum, seasonId, matchingMode)

        val resp = RequestManager.call(api)
        return resp.body<UserStatsResponse>()
    }



}