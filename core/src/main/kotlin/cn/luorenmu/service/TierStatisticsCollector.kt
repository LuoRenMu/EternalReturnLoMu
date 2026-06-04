package cn.luorenmu.service

import cn.luorenmu.exception.MessageReplyException
import cn.luorenmu.request.api.Api.Companion.ioAsync
import cn.luorenmu.request.api.entity.response.dakgg.DakGGLeaderboardResponse
import cn.luorenmu.request.api.entity.response.dakgg.resolveCutoffs
import cn.luorenmu.request.api.impl.EternalReturnDakGGApi
import cn.luorenmu.request.entity.module.DakGGServerName
import cn.luorenmu.request.entity.module.DakGGTeamMode
import cn.luorenmu.service.entity.TierStatistics
import kotlinx.coroutines.coroutineScope
import java.util.stream.Collectors

/**
 * @author LoMu
 * Date 2025/11/21 14:20
 */
class TierStatisticsCollector {

    suspend fun collect(serverName: DakGGServerName): TierStatistics {
        val (leaderboard, td, season) = coroutineScope {
            val leaderboardDeferred = ioAsync {
                val type = EternalReturnDakGGApi.Data.GetCurrentSeason.execute().type
                EternalReturnDakGGApi.Leaderboard.GetLeaderboard(1, type, serverName, DakGGTeamMode.Squad).execute()
            }
            val tierDistributionDeferred = ioAsync {
                EternalReturnDakGGApi.Statistics.GetTierDistribution(DakGGTeamMode.Squad).execute()
            }
            val seasonDF = ioAsync { EternalReturnDakGGApi.Data.GetCurrentSeason.execute() }
            Triple(leaderboardDeferred.await(), tierDistributionDeferred.await(), seasonDF.await())
        }

        val tierTypes = td.distributions.stream().map { ds -> ds.tierType }.distinct().sorted { o1, o2 ->
            val i1 = if (o1 < 10) o1 * 10 else o1
            val i2 = if (o2 < 10) o2 * 10 else o2
            i1 - i2
        }.collect(Collectors.toList())

        val count = mutableMapOf<Int, Int>()
        val rate = mutableMapOf<Int, Double>()

        for (distribution in td.distributions) {
            count[distribution.tierType]?.let {
                count[distribution.tierType] = it + distribution.count
            } ?: run {
                count[distribution.tierType] = distribution.count
            }
            rate[distribution.tierType]?.let {
                rate[distribution.tierType] = it + distribution.rate
            } ?: run {
                rate[distribution.tierType] = distribution.rate
            }
        }

        val (eternal, demigod) = leaderboard.cutoffs.resolveCutoffs()

        val rateStr = rate.mapValues { String.format("%.2f", it.value * 100) }.mapKeys { it.key.toString() }
        val tierTypesStr = tierTypes.map { it.toString() }
        val countStr = count.mapKeys { it.key.toString() }
        return TierStatistics(
            season.name,
            tierTypesStr,
            countStr,
            rateStr,
            eternal,
            demigod
        )
    }
}
