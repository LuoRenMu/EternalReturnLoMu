package cn.luorenmu.command

import cn.luorenmu.command.entity.MessageSender
import cn.luorenmu.common.annotation.CommandFilter
import cn.luorenmu.render.PlayerPageRender
import cn.luorenmu.request.api.EternalReturnOpenApiClient
import cn.luorenmu.request.entity.module.MatchingMode
import cn.luorenmu.service.ResourcesDownloadService
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import love.forte.simbot.message.Message
import love.forte.simbot.message.toText
import org.koin.java.KoinJavaComponent.inject

/**
 *
 * @author LoMu
 * Date 2025/10/24 14:06
 */

@CommandFilter("search <nickname> <t>")
class SearchPlayer : CommandEvent {

    private val log = KotlinLogging.logger {}

    private val render: PlayerPageRender by inject(PlayerPageRender::class.java)
    private val resourcesDownloadService: ResourcesDownloadService by inject(ResourcesDownloadService::class.java)
    private val executors: ExecutorCoroutineDispatcher by inject(
        ExecutorCoroutineDispatcher::class.java
    )


    override suspend fun listen(sender: MessageSender): Message {
        if (sender.command.isEmpty() || sender.command["nickname"] == null) {
            return "请使用命令格式/search (!名称) (保留参数)".toText()
        }

        preheatRequest(sender.command["nickname"]!!)
        return render.render(sender.command["nickname"]!!)
    }

    private suspend fun preheatRequest(nickname: String) {
        coroutineScope {

            val user = EternalReturnOpenApiClient.getUserNumByUserNickName(nickname)
            launch(executors) {
                val dataCurrentSeason = EternalReturnOpenApiClient.getDataCurrentSeason()
                EternalReturnOpenApiClient.getUserStats(
                    user.user.userNum,
                    dataCurrentSeason.seasonID,
                    MatchingMode.Rank
                )
            }
            launch(executors) {
                val games = EternalReturnOpenApiClient.getGamesByUserNum(
                    user.user.userNum
                )
                resourcesDownloadService.gameDataDownload(games.userGames)
            }
        }
    }


}