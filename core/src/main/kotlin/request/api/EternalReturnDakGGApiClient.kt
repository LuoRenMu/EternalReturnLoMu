package cn.luorenmu.request.api

import cn.luorenmu.request.api.entity.response.dakgg.*
import cn.luorenmu.request.entity.module.DakGGServerName
import cn.luorenmu.request.entity.module.DakGGTeamMode
import com.github.benmanes.caffeine.cache.Caffeine
import io.ktor.client.call.*
import io.ktor.client.statement.HttpResponse
import java.util.concurrent.TimeUnit

/**
 *
 * @author LoMu
 * Date 2025/10/31 23:40
 */
object EternalReturnDakGGApiClient {

    suspend fun getDataSeasons(): DakGGSeasonResponse {
        return EternalReturnDakGGApi.Data.GetGameDataBySeason.call().body()
    }

    suspend fun getCharacters(): DakGGCharactersResponse {
        return EternalReturnDakGGApi.Data.GetCharacters.call().body()
    }

    suspend fun getTiers(): DakGGTiersResponse {
        return EternalReturnDakGGApi.Data.GetTiers.call().body()
    }

    suspend fun getDataCurrentSeason(): DakGGCurrentSeasonResponse {
        val resp =
            EternalReturnDakGGApi.Data.GetCurrentSeason.call()
        val season = resp.body<DakGGCurrentSeasonResponse>()
        return season
    }

    suspend fun getItems(): DakGGItemsResponse {
        return EternalReturnDakGGApi.Data.GetItems.call().body()
    }


    suspend fun getCutoffsAndLeaderboard(
        page: Int = 1,
        seasonType: String,
        serverName: DakGGServerName,
        teamMode: DakGGTeamMode,
    ): DakGGLeaderboardResponse {
        val leaderboardApi = EternalReturnDakGGApi.Leaderboard.GetLeaderboard(page, seasonType, serverName, teamMode)
        val resp = leaderboardApi.call()
        return resp.body<DakGGLeaderboardResponse>()
    }

    suspend fun getTierDistributions(teamMode: DakGGTeamMode): TierDistributionsResponse {
        val tierDistributionApi =
            EternalReturnDakGGApi.Statistics.GetTierDistribution(teamMode)
        val resp = tierDistributionApi.call()
        return resp.body<TierDistributionsResponse>()
    }

    suspend fun getWeapons(): DakGGWeaponResponse {
        return EternalReturnDakGGApi.Data.GetWeapons.call().body()
    }

    suspend fun getProfile(nickname: String): DakGGProfileResponse {
        return EternalReturnDakGGApi.User.GetProfile(nickname).call().body()
    }

    suspend fun getTacticalSkills(): DakGGTacticalSkillResponse {
        return EternalReturnDakGGApi.Data.GetTacticalSkills.call().body()
    }

    suspend fun getTraitSkills(): DakGGTraitSkillsResponse {
        return EternalReturnDakGGApi.Data.GetTraitSkills.call().body()
    }


}