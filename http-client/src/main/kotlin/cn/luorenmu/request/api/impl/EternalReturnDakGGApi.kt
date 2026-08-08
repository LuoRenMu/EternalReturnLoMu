package cn.luorenmu.request.api.impl

import cn.luorenmu.common.util.NickNameUtil
import cn.luorenmu.common.util.toPath
import cn.luorenmu.exception.MessageReplyException
import cn.luorenmu.exception.NotFoundNickNameException
import cn.luorenmu.request.RequestManager
import cn.luorenmu.request.api.PakeApi
import cn.luorenmu.request.api.PakeResourceApi
import cn.luorenmu.request.api.entity.module.CacheTime
import cn.luorenmu.request.api.entity.module.ImageResourcesType
import cn.luorenmu.request.api.entity.response.dakgg.*
import cn.luorenmu.request.entity.module.DakGGServerName
import cn.luorenmu.request.entity.module.DakGGTeamMode
import cn.luorenmu.request.entity.module.DakGGRank
import cn.luorenmu.request.entity.module.MatchingMode
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.call.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.*
import io.ktor.utils.io.*
import kotlinx.coroutines.delay
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder
import java.nio.file.Path
import kotlin.time.Duration.Companion.milliseconds

/**
 *
 * @author LoMu
 * Date 2025/10/31 23:02
 */

/**
 * 部分数据无法通过官方Api获取或官方Api更新不及时的紧急替换
 */

val log = KotlinLogging.logger { }

sealed class EternalReturnDakGGApi<T>(
    override var url: String,
    override var method: HttpMethod = HttpMethod.Get,
    override val headers: MutableMap<String, String> = mutableMapOf(),
    override val body: MutableMap<String, String> = mutableMapOf(),
    override val cacheTime: CacheTime = CacheTime.NULL,
) : PakeApi(url, method, headers, body, cacheTime) {
    override var baseUrl: String = "https://er.dakgg.io/api"


    abstract suspend fun execute(): T


    init {
        headers["User-Agent"] =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36 Edg/146.0.0.0"
    }

    /**
     * 清除该API端点的缓存并重新请求，用于缓存数据中找不到新元素时强制刷新
     */
    suspend fun refresh(): HttpResponse {
        RequestManager.cacheMap[cacheTime]?.invalidate(url)
        return call()
    }

    fun nameFoundCheck(nickname: String, body: String) {
        val json = Json.parseToJsonElement(body)
        json.jsonObject["error"]?.jsonObject?.let {
            if (it["status"]?.jsonPrimitive?.int == 404) {
                throw NotFoundNickNameException("没有找到的用户名称 (${nickname[0]}***) 请检查名称")
            }
        }
    }

    /**
     * 处理响应并检查错误
     */
    protected suspend inline fun <reified R> handleResponse(
        nickname: String,
        response: HttpResponse,
    ): R {
        return try {
            response.body()
        } catch (e: JsonConvertException) {
            nameFoundCheck(nickname, response.bodyAsText())
            log.error { "DakGG服务器返回非预期数据,无法正常处理 ${e.printStack()}" }
            throw MessageReplyException("DakGG服务器返回非预期数据,无法正常处理")
        }
    }

    sealed class Data<T>(
        url: String,
        method: HttpMethod = HttpMethod.Get,
        headers: MutableMap<String, String> = mutableMapOf(),
        body: MutableMap<String, String> = mutableMapOf(),
        cacheTime: CacheTime = CacheTime.WEEK,
    ) : EternalReturnDakGGApi<T>(url, method, headers, body, cacheTime) {

        object GetTiers : Data<DakGGTiersResponse>("/v1/data/tiers?hl=zh_cn") {
            override suspend fun execute(): DakGGTiersResponse {
                return call().body()
            }
        }

        object GetGameDataBySeason : Data<DakGGSeasonResponse>("/v1/data/seasons?hl=zh_CN") {
            override suspend fun execute(): DakGGSeasonResponse {
                return call().body()
            }
        }

        object GetCurrentSeason : Data<DakGGCurrentSeasonResponse>(
            "/v0/current-season?hl=zh_CN"
        ) {
            override suspend fun execute(): DakGGCurrentSeasonResponse =
                call().body()
        }


        object GetCharacters : Data<DakGGCharactersResponse>(
            "/v1/data/characters?hl=zh_CN"
        ) {
            override suspend fun execute(): DakGGCharactersResponse =
                call().body()
        }

        object GetItems : Data<DakGGItemsResponse>(
            "/v1/data/items?hl=zh-cn"
        ) {
            override suspend fun execute(): DakGGItemsResponse =
                call().body()
        }

        object GetWeapons : Data<DakGGWeaponResponse>(
            "/v1/data/masteries?hl=zh_cn"
        ) {
            override suspend fun execute(): DakGGWeaponResponse =
                call().body()
        }

        object GetTraitSkills : Data<DakGGTraitSkillsResponse>(
            "/v1/data/trait-skills?hl=zh_cn"
        ) {
            override suspend fun execute(): DakGGTraitSkillsResponse =
                call().body()
        }

        object GetTacticalSkills : Data<DakGGTacticalSkillResponse>(
            "/v1/data/tactical-skills?hl=zh_cn"
        ) {
            override suspend fun execute(): DakGGTacticalSkillResponse =
                call().body()
        }

        object GetInfusions : Data<DakGGInfusionsResponse>(
            "/v1/data/infusions?hl=zh_cn"
        ) {
            override suspend fun execute(): DakGGInfusionsResponse =
                call().body()
        }

        object GetSkills : Data<DakGGSkillsResponse>(
            "/v1/data/skills?hl=zh_cn"
        ) {
            override suspend fun execute(): DakGGSkillsResponse =
                call().body()
        }
    }

    /**
     * dak.gg 角色详情页（tab=introduction）抓取。
     *
     * 页面为 Next.js HTML，统计 JSON 藏在 `__NEXT_DATA__` 脚本中，需正则提取后解析。
     * 使用完整 URL（baseUrl 置空），不走 `/v1` 接口缓存。
     */
    class GetCharacterAnalysis(
        characterKey: String,
        teamMode: String,
        matchingMode: String,
        tier: String?,
    ) : EternalReturnDakGGApi<CharacterAnalysisResponse>(
        url = buildString {
            append(
                "https://dak.gg/er/characters/$characterKey?hl=zh_CN&tab=introduction" +
                        "&teamMode=$teamMode&matchingMode=$matchingMode"
            )
            if (tier != null) append("&tier=$tier")
        },
    ) {
        override var baseUrl: String = ""

        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
            explicitNulls = false
            allowStructuredMapKeys = true
        }

        private val nextDataRegex = Regex("""(?s)<script id="__NEXT_DATA__"[^>]*>(.*?)</script>""")

        override suspend fun execute(): CharacterAnalysisResponse {
            val html = call().bodyAsText()
            val match = nextDataRegex.find(html)
                ?: throw MessageReplyException("角色详情页解析失败,请稍后再试")
            return json.decodeFromString(match.groupValues[1])
        }
    }

    sealed class User<T>(
        url: String,
        method: HttpMethod = HttpMethod.Get,
        headers: MutableMap<String, String> = mutableMapOf(),
        body: MutableMap<String, String> = mutableMapOf(),
        cacheTime: CacheTime = CacheTime.MINUTES,
    ) : EternalReturnDakGGApi<T>(url, method, headers, body, cacheTime) {
        /**
         * 获取用户信息、不传入season默认当前赛季
         */
        class GetProfile(val nickname: String) :
            User<DakGGProfileResponse>(
                "/v1/players/${URLEncoder.encode(nickname, "UTF-8")}/profile"
            ) {
            override suspend fun execute(): DakGGProfileResponse {
                return handleResponse<DakGGProfileResponse>(nickname, call())
            }
        }

        /**
         * 请求DakGG服务器与官方服务器数据进行同步
         *
         * https://er.dakgg.io/api/v0/rpc/player-sync/by-name/%E9%BB%91%E6%A1%83%E5%BD%B1
         * 请求方法
         * GET
         *
         */
        class Sync(val nickname: String) :
            User<DakGGSyncResponse>(
                "/v0/rpc/player-sync/by-name/${URLEncoder.encode(nickname, "UTF-8")}"
            ) {

            private suspend fun retry(maxRetries: Int = 3): DakGGSyncResponse {
                val body = call().body<DakGGSyncResponse>()
                for (attempt in 1..maxRetries) {
                    try {
                        if (body.isNotFound()) {
                            throw NotFoundNickNameException("没有找到的用户名称 (${NickNameUtil.hideNickname(nickname)}) 请检查名称")
                        }
                        if (body.isSuccess()) {
                            return body
                        }
                        if (body.isRateLimited()) {
                            val waitTime = body.retryAfter?.toLong() ?: 1000L
                            delay(waitTime.milliseconds)
                            continue
                        }
                        return body
                    } catch (_: Exception) {
                        if (attempt < maxRetries) {
                            val backoffTime = 1000L * attempt
                            delay(backoffTime.milliseconds)
                        }
                    }
                }
                return body
            }

            override suspend fun execute(): DakGGSyncResponse {
                return retry()
            }

        }
    }


    sealed class Game<T>(
        url: String,
        method: HttpMethod = HttpMethod.Get,
        headers: MutableMap<String, String> = mutableMapOf(),
        body: MutableMap<String, String> = mutableMapOf(),
        cacheTime: CacheTime = CacheTime.MINUTES,
    ) : EternalReturnDakGGApi<T>(url, method, headers, body, cacheTime) {
        class GetGame(
            val nickname: String,
            seasonType: String? = null,
            matchingMode: MatchingMode = MatchingMode.All,
            teamMode: DakGGTeamMode = DakGGTeamMode.All,
            page: Int = 1,
        ) :
            Game<DakGGMatchesResponse>(
                "/v1/players/${
                    URLEncoder.encode(
                        nickname,
                        "UTF-8"
                    )
                }/matches?${if (seasonType == null) "" else "season=$seasonType"}&matchingMode=${matchingMode.dakGGMode}&teamMode=${teamMode.value}&page=${page}"
            ) {
            override suspend fun execute(): DakGGMatchesResponse {
                return handleResponse<DakGGMatchesResponse>(nickname, call())
            }
        }
    }

    sealed class Leaderboard<T>(
        url: String,
        method: HttpMethod = HttpMethod.Get,
        headers: MutableMap<String, String> = mutableMapOf(),
        body: MutableMap<String, String> = mutableMapOf(),
        cacheTime: CacheTime = CacheTime.HOUR,
    ) : EternalReturnDakGGApi<T>(url, method, headers, body, cacheTime) {

        class GetLeaderboard(
            page: Int,
            seasonType: String,
            serverName: DakGGServerName,
            teamMode: DakGGTeamMode,
        ) : Leaderboard<DakGGLeaderboardResponse>(
            "/v0/leaderboard?page=$page" +
                    "&seasonKey=$seasonType" +
                    "&serverName=${serverName.value}" +
                    "&teamMode=${teamMode.value}&hl=zh_CN"
        ) {
            override suspend fun execute(): DakGGLeaderboardResponse =
                call().body()
        }
    }


    sealed class Statistics<T>(
        url: String,
        method: HttpMethod = HttpMethod.Get,
        headers: MutableMap<String, String> = mutableMapOf(),
        body: MutableMap<String, String> = mutableMapOf(),
        cacheTime: CacheTime = CacheTime.HOUR,
    ) : EternalReturnDakGGApi<T>(url, method, headers, body, cacheTime) {

        class GetTierDistribution(
            teamMode: DakGGTeamMode,
        ) : Statistics<TierDistributionsResponse>(
            "/v0/statistics/tier-distribution?teamMode=${teamMode.value}&hl=zh_CN"
        ) {
            override suspend fun execute(): TierDistributionsResponse =
                call().body()
        }

        class GetCharacterStats(
            teamMode: DakGGTeamMode,
            matchingMode: MatchingMode,
            tier: DakGGRank,
        ) : Statistics<DakGGCharacterStatsResponse>(
            "/v1/character-stats?dt=7&matchingMode=${matchingMode.dakGGMode}&teamMode=${teamMode.value}&tier=${tier.value}"
        ) {
            override suspend fun execute(): DakGGCharacterStatsResponse =
                call().body()
        }

    }

    sealed class Image(
        url: String,
        path: Path,
        method: HttpMethod = HttpMethod.Get,
        headers: MutableMap<String, String> = mutableMapOf(),
        body: MutableMap<String, String> = mutableMapOf(),
    ) : PakeResourceApi(url, path, method, headers, body) {
        override var baseUrl: String = ""

        /**
         * 非固定的url数据源，需要单独处理
         * 保证输出路径一直
         */
        class DakGGImageUrlCharacter(
            url: String,
            characterId: Int,
            skinId: Long,
            imageType: DakGGCharacterImgType,
        ) :
            Image(
                url.replace(DakGGCharacterImgType.regex(), imageType.value),
                path = when (imageType) {
                    DakGGCharacterImgType.CharProfile -> ImageResourcesType.Character.getCharacterPath(
                        characterId, skinId,
                        DakGGCharacterImgType.CharProfile
                    )

                    DakGGCharacterImgType.CharResult -> ImageResourcesType.Character.getCharacterPath(
                        characterId, skinId,
                        DakGGCharacterImgType.CharResult
                    )
                }.toPath()
            )

        class DakGGImageUrlItemBg(
            name: String,
        ) : Image(
            "//cdn.dak.gg/er/images/item/ico-itemgradebg-0${name}.svg",
            path = ImageResourcesType.ItemBg.getGeneralPath(name).toPath()
        )

        class DakGGImageUrlResources(
            url: String,
            imageResourcesType: ImageResourcesType,
            name: String,
        ) : Image(
            url,
            path = imageResourcesType.getGeneralPath(name).toPath()
        )

    }
}
