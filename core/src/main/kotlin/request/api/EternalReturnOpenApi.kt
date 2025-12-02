package cn.luorenmu.request.api

import cn.luorenmu.apiKey
import cn.luorenmu.request.api.entity.module.CacheTime
import cn.luorenmu.request.entity.module.MatchingMode
import cn.luorenmu.request.entity.module.MatchingTeamMode
import io.ktor.http.*
import java.net.URLEncoder

/**
 *
 * @author LoMu
 * Date 2025/10/25 15:24
 */
sealed class EternalReturnOpenApi(
    override var url: String,
    override var method: HttpMethod = HttpMethod.Companion.Get,
    override val headers: MutableMap<String, String> = mutableMapOf(),
    override val body: MutableMap<String, String> = mutableMapOf(),
    override val cacheTime: CacheTime = CacheTime.NULL,
) : PakeApi(url, method, headers, body, cacheTime) {
    init {
        headers.putAll(apiKey)
    }
    override var baseUrl: String = "https://open-api.bser.io"
    sealed class User(
        url: String,
        method: HttpMethod = HttpMethod.Companion.Get,
        headers: MutableMap<String, String> = mutableMapOf(),
        body: MutableMap<String, String> = mutableMapOf(),
        cacheTime: CacheTime = CacheTime.FIVE_MINUTES,
    ) : EternalReturnOpenApi(url, method, headers, body, cacheTime) {

        class GetIdByNickName(nickname: String) :
            User("/v1/user/nickname?query=${URLEncoder.encode(nickname, "UTF-8")}", HttpMethod.Companion.Get)


        class GetUserStats(userId: String, seasonId: Int, matchingMode: MatchingMode) :
            User("/v2/user/stats/uid/${userId}/${seasonId}/${matchingMode.value}", HttpMethod.Companion.Get)


    }

    sealed class Game(
        url: String,
        method: HttpMethod = HttpMethod.Companion.Get,
        headers: MutableMap<String, String> = mutableMapOf(),
        body: MutableMap<String, String> = mutableMapOf(),
        cacheTime: CacheTime = CacheTime.FIVE_MINUTES,
    ) : EternalReturnOpenApi(url, method, headers, body, cacheTime) {
        class GetGamesByUserId(userId: String) : Game("/v1/user/games/uid/${userId}", HttpMethod.Companion.Get)

        class GetGameByGameId(gameId: Long) : Game("/v1/games/${gameId}", HttpMethod.Companion.Get)
    }


    sealed class Data(
        url: String, method: HttpMethod = HttpMethod.Companion.Get,
        headers: MutableMap<String, String> = mutableMapOf(),
        body: MutableMap<String, String> = mutableMapOf(),
        cacheTime: CacheTime = CacheTime.ONE_WEEK,
    ) : EternalReturnOpenApi(url, method, headers, body, cacheTime) {

        /**
         * Meta Type, use 'hash' to find all types
         */
        object GetGameDataByHash : Data("/v2/data/hash", HttpMethod.Companion.Get)

        object GetGameDataBySeason :
            Data("/v1/data/Season", HttpMethod.Companion.Get)

        class GetGameDataByMetaType(metaType: String) : Data("/v2/data/${metaType}", HttpMethod.Companion.Get)
    }

    sealed class Rank(
        url: String, method: HttpMethod = HttpMethod.Companion.Get,
        headers: MutableMap<String, String> = mutableMapOf(),
        body: MutableMap<String, String> = mutableMapOf(),
        cacheTime: CacheTime = CacheTime.ONE_HOUR,
    ) : EternalReturnOpenApi(url, method, headers, body, cacheTime) {


        class GetGlobalRank(seasonId: Int, matchingTeamMode: MatchingTeamMode) :
            Rank("/v1/rank/top/${seasonId}/${matchingTeamMode.value}", HttpMethod.Companion.Get)
    }
}