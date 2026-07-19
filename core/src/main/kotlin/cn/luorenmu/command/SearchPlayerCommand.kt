package cn.luorenmu.command

import cn.luorenmu.Adapter
import cn.luorenmu.command.entity.CommandOptional
import cn.luorenmu.command.entity.MessageSender
import cn.luorenmu.common.annotation.BotCommand
import cn.luorenmu.common.util.NickNameUtil
import cn.luorenmu.common.util.PathUtils
import cn.luorenmu.render.RenderScreenshotPipeline
import cn.luorenmu.repository.PlayerAliasRepository
import cn.luorenmu.repository.StatisticsRepository
import cn.luorenmu.request.api.Api.Companion.ioAsync
import cn.luorenmu.request.api.Api.Companion.ioLaunch
import cn.luorenmu.request.api.entity.response.dakgg.DakGGCharactersResponse
import cn.luorenmu.request.api.entity.response.dakgg.DakGGCurrentSeasonResponse
import cn.luorenmu.request.api.entity.response.dakgg.DakGGProfileResponse
import cn.luorenmu.request.api.entity.response.dakgg.DakGGTiersResponse
import cn.luorenmu.request.api.entity.response.game.BattleUserGamesResponse.UserGame
import cn.luorenmu.request.api.impl.EternalReturnDakGGApi
import cn.luorenmu.request.entity.module.MatchingMode
import cn.luorenmu.service.PlayerRenderAssembler
import cn.luorenmu.service.ResourcesDownloadService
import com.github.benmanes.caffeine.cache.Caffeine
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDate
import love.forte.simbot.message.Message
import love.forte.simbot.message.OfflineImage
import love.forte.simbot.message.toText
import org.koin.java.KoinJavaComponent.inject
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 *
 * @author LoMu
 * Date 2025/10/24 14:06
 */

@BotCommand(
    "search",
    "search",
    "<nickname> <mode>",
    adapter = [Adapter.QG_BOT, Adapter.ONE_BOT]
)
class SearchPlayerCommand : CommandEvent {
    override val example: String = "/search RIOORI 排位"
    override val optionals: List<CommandOptional> =
        listOf(
            CommandOptional(name = "nickname", description = "玩家名称 当为空时检是否存在别名", required = false),
            CommandOptional(name = "mode", description = "查询模式 只对左边简介有影响 默认排位", required = false),
        )
    override val description = "查询玩家战绩及段位"

    private val log = KotlinLogging.logger {}
    private val resourcesDownloadService: ResourcesDownloadService by inject(ResourcesDownloadService::class.java)
    private val playerRenderAssembler: PlayerRenderAssembler by inject(PlayerRenderAssembler::class.java)
    private val statisticsService: StatisticsRepository by inject(StatisticsRepository::class.java)
    private val aliasRepository: PlayerAliasRepository by inject(PlayerAliasRepository::class.java)

    private val cache = Caffeine.newBuilder()
        .maximumSize(500)
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .build<String, String>()

    private val keyMutexes = ConcurrentHashMap<String, Mutex>()

    override suspend fun listen(sender: MessageSender, command: Map<String, String>): Message {
        val inputName = command["nickname"]
        val actualNickname = aliasRepository.resolveAlias(
            alias = inputName ?: PlayerAliasCommand.MY_SELF,
            groupId = sender.groupOpenId.toString(),
            userId = sender.senderOpenId.toString(),
        ) ?: inputName

        actualNickname ?: return "未设置别名或未输入昵称".toText()

        if (!NickNameUtil.isValidNickname(actualNickname)) {
            return "[${NickNameUtil.hideNickname(actualNickname)}]该名称不合法,EternalReturn不允许使用这样的名称".toText()
        }
        val mode = MatchingMode.convert(command["mode"])
        val cacheKey = "$actualNickname:${mode.value}"

        cache.getIfPresent(cacheKey)?.let { return OfflineImage.fileOfflineImage(it) }

        val mutex = keyMutexes.computeIfAbsent(cacheKey) { Mutex() }
        return mutex.withLock {
            cache.getIfPresent(cacheKey)?.let { return@withLock OfflineImage.fileOfflineImage(it) }
            val preheated = preheatRequest(actualNickname)
            val outputPath = PathUtils.resourcesPathResolve("render", "player", "$actualNickname-${mode.value}.png")
            RenderScreenshotPipeline.renderAndScreenshot(
                "search_player.ftl",
                playerRenderAssembler.assemble(preheated.profile, preheated.games, preheated.characters, preheated.tiers, preheated.season, mode, actualNickname),
                outputPath,
                "#content-container",
            )
            cache.put(cacheKey, outputPath.toString())
            keyMutexes.remove(cacheKey)
            statisticsService.incrementNicknameQueryCount(actualNickname)
            OfflineImage.fileOfflineImage(outputPath.toString())
        }
    }

    private data class PreheatedData(
        val profile: DakGGProfileResponse,
        val games: MutableList<UserGame>,
        val characters: DakGGCharactersResponse,
        val tiers: DakGGTiersResponse,
        val season: DakGGCurrentSeasonResponse,
    )

    private suspend fun preheatRequest(nickname: String): PreheatedData {
        return coroutineScope {
            ioLaunch { EternalReturnDakGGApi.User.Sync(nickname).execute() }

            val gamesDF = ioAsync { EternalReturnDakGGApi.Game.GetGame(nickname).execute() }
            val profileDF = ioAsync { EternalReturnDakGGApi.User.GetProfile(nickname).execute() }
            val charactersDF = ioAsync { EternalReturnDakGGApi.Data.GetCharacters.execute() }
            val tiersDF = ioAsync { EternalReturnDakGGApi.Data.GetTiers.execute() }
            val seasonDF = ioAsync { EternalReturnDakGGApi.Data.GetCurrentSeason.execute() }

            val games = gamesDF.await()
            val profile = profileDF.await()
            val characters = charactersDF.await()
            val tiers = tiersDF.await()
            val season = seasonDF.await()

            ioLaunch {
                resourcesDownloadService.gameDataDownload(games.matches)
                log.debug { "gameDataDownload 预备请求数据已完成" }
            }
            ioLaunch {
                resourcesDownloadService.downloadProfileData(profile)
                log.debug { "downloadProfileData 预备请求数据已完成" }
            }

            PreheatedData(profile, games.matches, characters, tiers, season)
        }
    }
}
