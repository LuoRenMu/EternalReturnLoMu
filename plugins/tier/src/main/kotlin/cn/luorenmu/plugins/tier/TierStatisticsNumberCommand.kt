package cn.luorenmu.plugins.tier

import cn.luorenmu.Adapter
import cn.luorenmu.command.CommandEvent
import cn.luorenmu.command.entity.CommandOptional
import cn.luorenmu.command.entity.MessageSender
import cn.luorenmu.common.annotation.BotCommand
import cn.luorenmu.common.util.PathUtils
import cn.luorenmu.common.util.RenderedFileCache
import cn.luorenmu.nutdraw.NutDraw
import cn.luorenmu.request.api.Api.Companion.ioLaunch
import cn.luorenmu.request.api.impl.EternalReturnDakGGApi
import cn.luorenmu.request.entity.module.DakGGServerName
import cn.luorenmu.request.entity.module.DakGGTeamMode
import cn.luorenmu.service.ResourcesDownloadService
import kotlinx.coroutines.coroutineScope
import love.forte.simbot.message.Message
import love.forte.simbot.message.OfflineImage
import org.koin.java.KoinJavaComponent.inject

/**
 *
 * @author LoMu
 * Date 2025/11/1 00:39
 */
@BotCommand("永恒/半神分段", "段位统计", "<server>", adapter = [Adapter.QG_BOT, Adapter.ONE_BOT])

class TierStatisticsNumberCommand : CommandEvent {
    override val example: String = "/段位统计"
    override val optionals: List<CommandOptional> = listOf(
        CommandOptional( "server", "服务器名称,默认为亚1",false),
    )
    override val description = "永恒/半身分段统计"

    private val resourcesDownloadService: ResourcesDownloadService by inject(ResourcesDownloadService::class.java)
    private val tierStatisticsCollector = TierStatisticsCollector()

    override suspend fun listen(sender: MessageSender, command: Map<String, String>): Message {
        val serverName = command["server"]?.let { DakGGServerName.convert(it) } ?: DakGGServerName.Asia
        val outputPath = PathUtils.resourcesPathResolve(
            "render",
            "tier",
            "tierStatisticsNumber_${CACHE_VERSION}_${serverName.value}.png",
        )
        val cachedPath = RenderedFileCache.getOrCreate(
            path = outputPath,
            cleanupPrefix = "tierStatisticsNumber_",
        ) { path ->
            preheatRequest(serverName)
            val cutoffsAndTierNumber = tierStatisticsCollector.collect(serverName)
            NutDraw.render(TierStatisticsTemplate(), cutoffsAndTierNumber, path)
        }
        return OfflineImage.fileOfflineImage(cachedPath.toString())
    }

    /**
     * 提前请求好数据由底层缓存
     */
    private suspend fun preheatRequest(serverName: DakGGServerName) {
        coroutineScope {
            ioLaunch {
                EternalReturnDakGGApi.Statistics.GetTierDistribution(DakGGTeamMode.Squad)
                    .execute()
            }
            ioLaunch {
                val type = EternalReturnDakGGApi.Data.GetCurrentSeason.execute().type
                EternalReturnDakGGApi.Leaderboard.GetLeaderboard(
                    1,
                    type,
                    serverName,
                    DakGGTeamMode.Squad
                ).execute().cutoffs.forEach { it.tierType }
            }

            val tiers = EternalReturnDakGGApi.Data.GetTiers.execute()
            resourcesDownloadService.downloadTiers(tiers)
        }
    }

    private companion object {
        const val CACHE_VERSION = "v1"
    }
}
