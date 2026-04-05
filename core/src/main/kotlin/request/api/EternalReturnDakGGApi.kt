package cn.luorenmu.request.api

import cn.luorenmu.common.util.toPath
import cn.luorenmu.request.api.entity.module.CacheTime
import cn.luorenmu.request.api.entity.module.ImageResourcesType
import cn.luorenmu.request.api.entity.response.dakgg.*
import cn.luorenmu.request.entity.module.DakGGServerName
import cn.luorenmu.request.entity.module.DakGGTeamMode
import cn.luorenmu.request.entity.module.MatchingMode
import io.ktor.client.call.*
import io.ktor.http.*
import java.net.URLEncoder
import java.nio.file.Path

/**
 *
 * @author LoMu
 * Date 2025/10/31 23:02
 */

/**
 * 部分数据无法通过官方Api获取或官方Api更新不及时的紧急替换
 */
sealed class EternalReturnDakGGApi<T>(
    override var url: String,
    override var method: HttpMethod = HttpMethod.Get,
    override val headers: MutableMap<String, String> = mutableMapOf(),
    override val body: MutableMap<String, String> = mutableMapOf(),
    override val cacheTime: CacheTime = CacheTime.NULL,
) : PakeApi(url, method, headers, body, cacheTime) {
    override var baseUrl: String = "https://er.dakgg.io/api"
    abstract suspend fun execute(): T


    sealed class Data<T>(
        url: String,
        method: HttpMethod = HttpMethod.Get,
        headers: MutableMap<String, String> = mutableMapOf(),
        body: MutableMap<String, String> = mutableMapOf(),
        cacheTime: CacheTime = CacheTime.ONE_WEEK,
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
    }

    sealed class User<T>(
        url: String,
        method: HttpMethod = HttpMethod.Get,
        headers: MutableMap<String, String> = mutableMapOf(),
        body: MutableMap<String, String> = mutableMapOf(),
        cacheTime: CacheTime = CacheTime.FIVE_MINUTES,
    ) : EternalReturnDakGGApi<T>(url, method, headers, body, cacheTime) {
        /**
         * 获取用户信息、不传入season默认当前赛季
         */
        class GetProfile(nickname: String) :
            User<DakGGProfileResponse>(
                "/v1/players/${URLEncoder.encode(nickname, "UTF-8")}/profile"
            ) {
            override suspend fun execute(): DakGGProfileResponse =
                call().body()
        }
    }


    sealed class Game<T>(
        url: String,
        method: HttpMethod = HttpMethod.Get,
        headers: MutableMap<String, String> = mutableMapOf(),
        body: MutableMap<String, String> = mutableMapOf(),
        cacheTime: CacheTime = CacheTime.FIVE_MINUTES,
    ) : EternalReturnDakGGApi<T>(url, method, headers, body, cacheTime) {
        class GetGame(nickname: String, seasonType: String, matchingMode: MatchingMode = MatchingMode.All, teamMode: DakGGTeamMode = DakGGTeamMode.All, page: Int = 1) :
            Game<DakGGMatchesResponse>(
                "/v1/players/${URLEncoder.encode(nickname, "UTF-8")}/matches?season=${seasonType}&matchingMode=${matchingMode.dakGGMode}&teamMode=${teamMode.value}&page=${page}"
            ) {
            override suspend fun execute(): DakGGMatchesResponse {
               return call().body()
            }

        }
    }

    sealed class Leaderboard<T>(
        url: String,
        method: HttpMethod = HttpMethod.Get,
        headers: MutableMap<String, String> = mutableMapOf(),
        body: MutableMap<String, String> = mutableMapOf(),
        cacheTime: CacheTime = CacheTime.ONE_HOUR,
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
        cacheTime: CacheTime = CacheTime.ONE_HOUR,
    ) : EternalReturnDakGGApi<T>(url, method, headers, body, cacheTime) {

        class GetTierDistribution(
            teamMode: DakGGTeamMode,
        ) : Statistics<TierDistributionsResponse>(
            "/v0/statistics/tier-distribution?teamMode=${teamMode.value}&hl=zh_CN"
        ) {
            override suspend fun execute(): TierDistributionsResponse =
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
                path = ImageResourcesType.getCharacterPath(characterId, skinId, imageType).toPath()
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
