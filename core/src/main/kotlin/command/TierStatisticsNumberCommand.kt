package cn.luorenmu.command

import cn.luorenmu.command.entity.MessageSender
import cn.luorenmu.common.annotation.BotCommand
import cn.luorenmu.common.util.BrowserPool
import cn.luorenmu.common.util.PathUtils
import cn.luorenmu.render.FreemarkerRenderer
import cn.luorenmu.request.api.Api.Companion.ioLaunch
import cn.luorenmu.request.api.EternalReturnDakGGApi
import cn.luorenmu.request.entity.module.DakGGServerName
import cn.luorenmu.request.entity.module.DakGGTeamMode
import cn.luorenmu.service.EternalReturnRenderService
import cn.luorenmu.service.ResourcesDownloadService
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.coroutineScope
import love.forte.simbot.message.Message
import love.forte.simbot.message.OfflineImage
import org.koin.java.KoinJavaComponent.inject
import java.util.UUID

/**
 *
 * @author LoMu
 * Date 2025/11/1 00:39
 */
@BotCommand("永恒/半身分段", "段位统计", "<server>")
class TierStatisticsNumberCommand : CommandEvent {
    private val log = KotlinLogging.logger {}
    private val resourcesDownloadService: ResourcesDownloadService by inject(ResourcesDownloadService::class.java)
    private val eternalReturnRenderService: EternalReturnRenderService by inject(
        EternalReturnRenderService::class.java
    )


    override suspend fun listen(sender: MessageSender, command: Map<String, String>): Message? {
        val serverName = command["server"]?.let { DakGGServerName.convert(it) } ?: DakGGServerName.Asia
        preheatRequest(serverName)
        val cutoffsAndTierNumber = eternalReturnRenderService.getCutoffsAndTierNumber(serverName)
        val outputPath = PathUtils.resourcesPathResolve("render", "tier", "tierStatisticsNumber.png")
        val renderPath = PathUtils.resourcesPathResolve("render", "tier.html")
        val html = FreemarkerRenderer.render("tier_statistics_number.ftl", cutoffsAndTierNumber)
        renderPath.toFile().writeText(html)
        BrowserPool.getBrowser().screenshotSelector(
            renderPath.toString(),
            outputPath,
            "#app"
        )
        return OfflineImage.fileOfflineImage(outputPath.toString())
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
}

