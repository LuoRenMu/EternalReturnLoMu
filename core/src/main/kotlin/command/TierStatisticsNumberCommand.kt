package cn.luorenmu.command

import cn.luorenmu.command.entity.MessageSender
import cn.luorenmu.common.annotation.CommandFilter
import cn.luorenmu.render.TierStatisticsNumberRender
import cn.luorenmu.request.api.EternalReturnDakGGApiClient
import cn.luorenmu.request.entity.module.DakGGServerName
import cn.luorenmu.request.entity.module.DakGGTeamMode
import cn.luorenmu.service.ResourcesDownloadService
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import love.forte.simbot.message.Message
import org.koin.java.KoinJavaComponent.inject

/**
 *
 * @author LoMu
 * Date 2025/11/1 00:39
 */
@CommandFilter("tier",["段位统计"],"<server>")
class TierStatisticsNumberCommand() : CommandEvent {
    private val log = KotlinLogging.logger {}
    private val render = TierStatisticsNumberRender()
    private val resourcesDownloadService: ResourcesDownloadService by inject(ResourcesDownloadService::class.java)

    private val executors: ExecutorCoroutineDispatcher by inject(
        ExecutorCoroutineDispatcher::class.java
    )

    override suspend fun listen(sender: MessageSender,command: Map<String, String>): Message? {
        val serverName = command["server"]?.let { DakGGServerName.convert(it) } ?: DakGGServerName.Asia
        preheatRequest(serverName)
        return render.render(serverName)
    }

    private suspend fun preheatRequest(serverName: DakGGServerName) {
        coroutineScope {
            launch(executors) {
                EternalReturnDakGGApiClient.getTierDistributions(DakGGTeamMode.Squad).distributions.map { it.tierType }
            }
            launch(executors) {
                val type = EternalReturnDakGGApiClient.getDataCurrentSeason().type
                EternalReturnDakGGApiClient.getCutoffsAndLeaderboard(
                    1,
                    type,
                    serverName,
                    DakGGTeamMode.Squad
                ).cutoffs.map { it.tierType }
            }

            val tiers = EternalReturnDakGGApiClient.getTiers()
            resourcesDownloadService.downloadTiers(tiers)
        }
    }
}

