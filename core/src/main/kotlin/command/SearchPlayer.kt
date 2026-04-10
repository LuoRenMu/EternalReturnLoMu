package cn.luorenmu.command

import cn.luorenmu.command.entity.MessageSender
import cn.luorenmu.common.annotation.BotCommand
import cn.luorenmu.common.util.BrowserPool
import cn.luorenmu.common.util.PathUtils
import cn.luorenmu.render.FreemarkerRenderer
import cn.luorenmu.request.api.Api.Companion.ioLaunch
import cn.luorenmu.request.api.impl.EternalReturnDakGGApi
import cn.luorenmu.request.api.impl.EternalReturnOpenApi
import cn.luorenmu.request.entity.module.MatchingMode
import cn.luorenmu.service.EternalReturnRenderService
import cn.luorenmu.service.ResourcesDownloadService
import com.github.benmanes.caffeine.cache.Caffeine
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.coroutineScope
import love.forte.simbot.message.Message
import love.forte.simbot.message.OfflineImage
import love.forte.simbot.message.toText
import org.koin.java.KoinJavaComponent.inject
import java.util.*
import java.util.concurrent.TimeUnit

/**
 *
 * @author LoMu
 * Date 2025/10/24 14:06
 */

@BotCommand(
    "查询玩家战绩",
    "search",
    "<nickname> <mode>",
    description = "查询玩家战绩 示例:\n /search LoMu \n /search LoMu 匹配\n /search LoMu 排位 "
)
class SearchPlayer : CommandEvent {

    private val log = KotlinLogging.logger {}
    private val resourcesDownloadService: ResourcesDownloadService by inject(ResourcesDownloadService::class.java)
    private val eternalReturnRenderService: EternalReturnRenderService by inject(
        EternalReturnRenderService::class.java
    )


    private val cache = Caffeine.newBuilder()
        .maximumSize(500)
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .build<String, String>()


    override suspend fun listen(sender: MessageSender, command: Map<String, String>): Message {
        if (command.isEmpty() || command["nickname"] == null) {
            return "请使用命令格式/search (!名称)".toText()
        }
        val nickname = command["nickname"]!!
        if (cache.getIfPresent(nickname) == null) {
            synchronized(this) {
                if (cache.getIfPresent(nickname) != null) {
                    return OfflineImage.fileOfflineImage(cache.getIfPresent(nickname).toString())
                }
            }
        }


        val mode = MatchingMode.convert(command["mode"])
        preheatRequest(nickname)

        val outputPath = PathUtils.resourcesPathResolve("render", "player", "$nickname.png")
        val renderPath = PathUtils.resourcesPathResolve("render", "player", "tmp", "${UUID.randomUUID()}.html")
        val html =
            FreemarkerRenderer.render(
                "search_player.ftl",
                eternalReturnRenderService.getEternalReturnRender(nickname, mode)
            )
        renderPath.toFile().writeText(html)
        BrowserPool.getBrowser()
            .screenshotSelector(renderPath.toString(), outputPath, "#content-container")
        cache.put(nickname, outputPath.toString())
        return OfflineImage.fileOfflineImage(outputPath.toString())
    }

    private suspend fun preheatRequest(nickname: String) {
        coroutineScope {
            ioLaunch {
                EternalReturnDakGGApi.User.Sync(nickname).execute()
            }
            val games = EternalReturnDakGGApi.Game.GetGame(nickname).execute()
            ioLaunch {

                // eternal return dev api time out
//                val games = EternalReturnOpenApi.Game.GetGamesByUserId(
//                    user.user.userId
//                ).execute()
                resourcesDownloadService.gameDataDownload(games.matches)
                log.debug { "gameDataDownload 预备请求数据已完成" }
            }
            ioLaunch {
                resourcesDownloadService.downloadProfileData(nickname)
                log.debug { "downloadProfileData 预备请求数据已完成" }
            }
        }
    }


}