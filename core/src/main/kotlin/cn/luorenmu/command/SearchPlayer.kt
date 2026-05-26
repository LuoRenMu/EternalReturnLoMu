package cn.luorenmu.command

import cn.luorenmu.Adapter
import cn.luorenmu.command.entity.CommandOptional
import cn.luorenmu.command.entity.MessageSender
import cn.luorenmu.common.annotation.BotCommand
import cn.luorenmu.common.util.PathUtils
import cn.luorenmu.render.RenderScreenshotPipeline
import cn.luorenmu.repository.StatisticsRepository
import cn.luorenmu.request.api.Api.Companion.ioLaunch
import cn.luorenmu.request.api.impl.EternalReturnDakGGApi
import cn.luorenmu.request.entity.module.MatchingMode
import cn.luorenmu.service.PlayerRenderAssembler
import cn.luorenmu.service.ResourcesDownloadService
import com.github.benmanes.caffeine.cache.Caffeine
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import love.forte.simbot.message.Message
import love.forte.simbot.message.OfflineImage
import love.forte.simbot.message.toText
import org.koin.java.KoinJavaComponent.inject
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
    adapter = [Adapter.QG_BOT, Adapter.ONE_BOT]
)
class SearchPlayer : CommandEvent {
    override val example: String = "/search RIOORI 排位"
    override val optionals: List<CommandOptional> =
        listOf(
            CommandOptional(
                name = "nickname",
                description = "玩家名称",
                required = true
            ),
            CommandOptional(
                name = "mode",
                description = "查询模式 只对左边简介有影响 默认排位",
                required = false
            ),
        )
    override val description = "查询玩家战绩及段位"

    private val log = KotlinLogging.logger {}
    private val resourcesDownloadService: ResourcesDownloadService by inject(ResourcesDownloadService::class.java)
    private val playerRenderAssembler: PlayerRenderAssembler by inject(
        PlayerRenderAssembler::class.java
    )
    private val statisticsService: StatisticsRepository by inject(StatisticsRepository::class.java)

    private val cache = Caffeine.newBuilder()
        .maximumSize(500)
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .build<String, String>()

    private val cacheMutex = Mutex()

    override suspend fun listen(sender: MessageSender, command: Map<String, String>): Message {
        if (command.isEmpty() || command["nickname"] == null) {
            return "请使用命令格式/search (!名称)".toText()
        }
        val nickname = command["nickname"]!!

        statisticsService.recordCommandUsage("search", nickname)
        statisticsService.incrementNicknameQueryCount(nickname)

        // 快路径：缓存命中直接返回
        cache.getIfPresent(nickname)?.let {
            return OfflineImage.fileOfflineImage(it)
        }

        val mode = MatchingMode.convert(command["mode"])

        // 慢路径：持锁执行，防止并发重复渲染
        return cacheMutex.withLock {
            // 双重检查
            cache.getIfPresent(nickname)?.let {
                return@withLock OfflineImage.fileOfflineImage(it)
            }

            preheatRequest(nickname)
            val outputPath = PathUtils.resourcesPathResolve("render", "player", "$nickname.png")
            RenderScreenshotPipeline.renderContentAndScreenshot(
                "search_player.ftl",
                playerRenderAssembler.assemble(nickname, mode),
                outputPath,
                "#content-container"
            )
            cache.put(nickname, outputPath.toString())
            OfflineImage.fileOfflineImage(outputPath.toString())
        }
    }

    private suspend fun preheatRequest(nickname: String) {
        coroutineScope {
            ioLaunch {
                EternalReturnDakGGApi.User.Sync(nickname).execute()
            }
            val games = EternalReturnDakGGApi.Game.GetGame(nickname).execute()
            ioLaunch {
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
