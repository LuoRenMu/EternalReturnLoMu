package cn.luorenmu.request.api.impl

import cn.luorenmu.apiKey
import cn.luorenmu.exception.ForbiddenException
import cn.luorenmu.exception.NotFoundNickNameException
import cn.luorenmu.request.api.PakeApi
import cn.luorenmu.request.api.entity.module.CacheTime
import cn.luorenmu.request.api.entity.response.data.BaseGameDataResponse
import cn.luorenmu.request.api.entity.response.data.GameDataSeasonResponse
import cn.luorenmu.request.api.entity.response.game.BattleUserGamesResponse
import cn.luorenmu.request.api.entity.response.user.UserNickNameResponse
import cn.luorenmu.request.api.entity.response.user.UserStatsResponse
import cn.luorenmu.request.entity.module.MatchingMode
import cn.luorenmu.request.entity.module.MatchingTeamMode
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.call.body
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.random.Random
import love.forte.simbot.message.toText
import java.net.URLEncoder
import java.time.LocalDateTime

/**
 *
 * @author LoMu
 * Date 2025/10/25 15:24
 */
sealed class EternalReturnOpenApi<T>(
    override var url: String,
    override var method: HttpMethod = HttpMethod.Get,
    override val headers: MutableMap<String, String> = mutableMapOf(),
    override val body: MutableMap<String, String> = mutableMapOf(),
    override val cacheTime: CacheTime = CacheTime.NULL,
) : PakeApi(url, method, headers, body, cacheTime) {
    init {
        headers.putAll(apiKey)
    }

    override var baseUrl: String = "https://open-api.bser.io"

    private val log = KotlinLogging.logger {}

    /**
     * 拦截 API 调用并在遇到限流或禁止访问时重试
     */
    override suspend fun call(): HttpResponse {
        val response = super.call()
        val jsonObject = response.body<JsonObject>().jsonObject
        jsonObject["message"]?.jsonPrimitive?.content?.let {
            if (it == "Too Many Requests") {
                log.debug { "EternalReturnOpenApi Too Many Requests retry $url" }
                delay(Random.nextLong(1000, 3000))
                return call()
            }
            if (it == "Forbidden") {
                log.debug { "EternalReturnOpenApi Forbidden $url" }
                throw ForbiddenException()
            }
        }
        return response
    }

    abstract suspend fun execute(): T

    sealed class User<T>(
        url: String,
        cacheTime: CacheTime = CacheTime.FIVE_MINUTES,
    ) : EternalReturnOpenApi<T>(url, cacheTime = cacheTime) {

        class GetIdByNickName(
            private val nickname: String
        ) : User<UserNickNameResponse>(
            "/v1/user/nickname?query=${URLEncoder.encode(nickname, "UTF-8")}"
        ) {
            override suspend fun execute(): UserNickNameResponse {
                val resp = call()
                val body = resp.body<JsonObject>()

                if (resp.status.value == 404 ||
                    body.jsonObject["code"]?.jsonPrimitive?.content == "404"
                ) {
                    throw NotFoundNickNameException(
                        "没有找到的用户名称 (${nickname[0]}***) 请检查名称".toText()
                    )
                }

                return resp.body()
            }
        }

        class GetUserStatsV1(
            userId: String,
            seasonId: Int,
        ) : User<UserStatsResponse>(
            "/v1/user/stats/uid/$userId/$seasonId"
        ) {
            override suspend fun execute(): UserStatsResponse =
                call().body()
        }

        class GetUserStatsV2(
            userId: String,
            seasonId: Int,
            matchingMode: MatchingMode
        ) : User<UserStatsResponse>(
            "/v2/user/stats/uid/$userId/$seasonId/${matchingMode.value}"
        ) {
            override suspend fun execute(): UserStatsResponse =
                call().body()
        }
    }

    sealed class Game<T>(
        url: String,
        cacheTime: CacheTime = CacheTime.FIVE_MINUTES,
    ) : EternalReturnOpenApi<T>(url, cacheTime = cacheTime) {

        class GetGamesByUserId(
            userId: String
        ) : Game<BattleUserGamesResponse>(
            "/v1/user/games/uid/$userId"
        ) {
            override suspend fun execute(): BattleUserGamesResponse =
                call().body()
        }

        class GetGameByGameId(
            gameId: Long
        ) : Game<BattleUserGamesResponse>(
            "/v1/games/$gameId"
        ) {
            override suspend fun execute(): BattleUserGamesResponse =
                call().body()
        }
    }



    sealed class Data<T>(
        url: String,
        cacheTime: CacheTime = CacheTime.ONE_WEEK,
    ) : EternalReturnOpenApi<T>(url, cacheTime = cacheTime) {

        object GetGameDataByHash :
            Data<BaseGameDataResponse<JsonObject>>(
                "/v2/data/hash"
            ) {
            override suspend fun execute(): BaseGameDataResponse<JsonObject> =
                call().body()
        }

        object GetGameDataBySeason :
            Data<GameDataSeasonResponse>(
                "/v1/data/Season"
            ) {
            override suspend fun execute(): GameDataSeasonResponse {
                val resp = call()
                val seasons =
                    resp.body<BaseGameDataResponse<MutableList<GameDataSeasonResponse>>>()

                val season = seasons.data.first { it.isCurrent == 1 }

                // 官方 API 落后 → DAK.GG 补偿
                if (season.seasonEnd.isBefore(LocalDateTime.now())) {
                    val dakGGSeason =
                        EternalReturnDakGGApi.Data.GetCurrentSeason.execute()

                    if (dakGGSeason.id != season.seasonID) {
                        return dakGGSeason.convert()
                    }
                }
                return season
            }
        }

        class GetGameDataByMetaType(
            metaType: String
        ) : Data<BaseGameDataResponse<JsonObject>>(
            "/v2/data/$metaType"
        ) {
            override suspend fun execute(): BaseGameDataResponse<JsonObject> =
                call().body()
        }
    }


    sealed class Rank<T>(
        url: String,
        cacheTime: CacheTime = CacheTime.ONE_HOUR,
    ) : EternalReturnOpenApi<T>(url, cacheTime = cacheTime) {

        class GetGlobalRank(
            seasonId: Int,
            matchingTeamMode: MatchingTeamMode
        ) : Rank<JsonObject>(
            "/v1/rank/top/$seasonId/${matchingTeamMode.value}"
        ) {
            override suspend fun execute(): JsonObject =
                call().body()
        }
    }
}