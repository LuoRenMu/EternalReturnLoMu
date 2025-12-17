package cn.luorenmu.command

import cn.luorenmu.command.entity.MessageSender
import cn.luorenmu.common.annotation.BotCommand
import cn.luorenmu.common.util.BrowserPool
import cn.luorenmu.common.util.PathUtils
import cn.luorenmu.render.FreemarkerRenderer
import cn.luorenmu.request.api.Api.Companion.ioAsync
import cn.luorenmu.request.api.EternalReturnOpenApiClient
import cn.luorenmu.request.entity.module.MatchingMode
import cn.luorenmu.service.EternalReturnRenderService
import cn.luorenmu.service.ResourcesDownloadService
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.coroutineScope
import love.forte.simbot.message.Message
import love.forte.simbot.message.OfflineImage
import love.forte.simbot.message.toText
import org.koin.java.KoinJavaComponent.inject

/**
 *
 * @author LoMu
 * Date 2025/10/24 14:06
 */

@BotCommand("search", "search", "<nickname> <mode>")
class SearchPlayer : CommandEvent {

    private val log = KotlinLogging.logger {}
    private val resourcesDownloadService: ResourcesDownloadService by inject(ResourcesDownloadService::class.java)
    private val eternalReturnRenderService: EternalReturnRenderService by inject(
        EternalReturnRenderService::class.java
    )

    override suspend fun listen(sender: MessageSender, command: Map<String, String>): Message {
        if (command.isEmpty() || command["nickname"] == null) {
            return "请使用命令格式/search (!名称)".toText()
        }

        val nickname = command["nickname"]!!
        val mode = MatchingMode.convert(command["mode"]?.toInt())
        preheatRequest(nickname)
        val outputPath = PathUtils.resourcesPathResolve("render", "player", "$nickname.png")
        val html =
            FreemarkerRenderer.render(
                "search_player.ftl",
                eternalReturnRenderService.getEternalReturnRender(nickname, mode)
            )
        BrowserPool.getBrowser()
            .screenshotContentSelector(html, outputPath, "#content-container")
        return OfflineImage.fileOfflineImage(outputPath.toString())
    }

    private suspend fun preheatRequest(nickname: String) {
        coroutineScope {
            val user = EternalReturnOpenApiClient.getUserNumByUserNickName(nickname)
            ioAsync {
                val dataCurrentSeason = EternalReturnOpenApiClient.getDataCurrentSeason()
                EternalReturnOpenApiClient.getUserStats(
                    user.user.userId,
                    dataCurrentSeason.seasonID,
                    MatchingMode.Rank
                )
                log.debug { "getUserStats 预备请求数据已完成" }
            }
            ioAsync {
                val games = EternalReturnOpenApiClient.getGamesByUserNum(
                    user.user.userId
                )
                resourcesDownloadService.gameDataDownload(games.userGames)
                log.debug { "gameDataDownload 预备请求数据已完成" }
            }
            ioAsync {

                resourcesDownloadService.downloadProfileData(nickname)
                log.debug { "downloadProfileData 预备请求数据已完成" }
            }
            ioAsync {
                EternalReturnOpenApiClient.getGamesByUserNum(
                    user.user.userId
                )
                log.debug { "getGamesByUserNum 预备请求数据已完成" }
            }
        }
    }


}