package cn.luorenmu.plugins.player

import cn.luorenmu.Adapter
import cn.luorenmu.command.CommandEvent
import cn.luorenmu.command.entity.CommandOptional
import cn.luorenmu.command.entity.MessageSender
import cn.luorenmu.common.annotation.BotCommand
import cn.luorenmu.common.util.NickNameUtil
import cn.luorenmu.common.util.PathUtils
import cn.luorenmu.exception.MessageReplyException
import cn.luorenmu.nutdraw.NutDraw
import cn.luorenmu.repository.PlayerAliasRepository
import cn.luorenmu.repository.StatisticsRepository
import cn.luorenmu.request.api.Api.Companion.ioAsync
import cn.luorenmu.request.api.Api.Companion.ioLaunch
import cn.luorenmu.request.api.entity.response.dakgg.DakGGCharactersResponse
import cn.luorenmu.request.api.entity.response.dakgg.DakGGCurrentSeasonResponse
import cn.luorenmu.request.api.entity.response.dakgg.DakGGProfileResponse
import cn.luorenmu.request.api.entity.response.dakgg.DakGGTiersResponse
import cn.luorenmu.request.api.entity.response.dakgg.DakGGInfusionsResponse
import cn.luorenmu.request.api.entity.response.game.BattleUserGamesResponse.UserGame
import cn.luorenmu.request.api.impl.EternalReturnDakGGApi
import cn.luorenmu.request.entity.module.MatchingMode
import cn.luorenmu.service.ResourcesDownloadService
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDate
import love.forte.simbot.message.Message
import love.forte.simbot.message.OfflineImage
import love.forte.simbot.message.toText
import org.koin.java.KoinJavaComponent.inject
import java.time.LocalDateTime

/**
 *
 * @author LoMu
 * Date 2025/10/24 14:06
 */

@BotCommand(
    "search",
    "search",
    "<nickname> <mode>",
    aliases = ["查询玩家", "查玩家"],
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
    private val playerRenderAssembler = PlayerRenderAssembler()
    private val statisticsService: StatisticsRepository by inject(StatisticsRepository::class.java)
    private val aliasRepository: PlayerAliasRepository by inject(PlayerAliasRepository::class.java)

    private val renderMutexes = Array(64) { Mutex() }
    private val refreshScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
        val renderKey = "$actualNickname:${mode.value}"

        val mutex = renderMutexes[Math.floorMod(renderKey.hashCode(), renderMutexes.size)]
        return mutex.withLock {
            val preheated = preheatRequest(actualNickname)
            val outputPath = PathUtils.resourcesPathResolve("render", "player", "$actualNickname-${mode.value}.png")
            NutDraw.render(
                SearchPlayerTemplate(),
                playerRenderAssembler.assemble(preheated.profile, preheated.games, preheated.characters, preheated.tiers, preheated.season, preheated.infusions, mode, actualNickname),
                outputPath,
            )
            statisticsService.recordPlayerQuery(actualNickname, sender.senderOpenId.toString())
            OfflineImage.fileOfflineImage(outputPath.toString())
        }
    }

    private data class PreheatedData(
        val profile: DakGGProfileResponse,
        val games: MutableList<UserGame>,
        val characters: DakGGCharactersResponse,
        val tiers: DakGGTiersResponse,
        val season: DakGGCurrentSeasonResponse,
        val infusions: DakGGInfusionsResponse,
    )

    private suspend fun preheatRequest(nickname: String): PreheatedData {
        val profile = EternalReturnDakGGApi.User.GetProfile(nickname).execute()
        val latestSeasonId = profile.playerSeasons?.firstOrNull()?.seasonId
            ?: throw MessageReplyException("该玩家无任何赛季有游玩数据")

        return coroutineScope {
            // DAK.GG sync is advisory: the previous implementation launched it as a child
            // and still waited at coroutineScope exit. Refresh in the background so a slow
            // sync endpoint cannot delay the current cached/profile response.
            refreshScope.launch { runCatching { EternalReturnDakGGApi.User.Sync(nickname).execute() } }

            val seasonsDF = ioAsync { EternalReturnDakGGApi.Data.GetGameDataBySeason.execute() }
            val gamesDF = ioAsync {
                val seasonKey = seasonsDF.await().getSeasonById(latestSeasonId).key
                EternalReturnDakGGApi.Game.GetGame(nickname, seasonKey).execute()
            }
            val charactersDF = ioAsync { EternalReturnDakGGApi.Data.GetCharacters.execute() }
            val tiersDF = ioAsync { EternalReturnDakGGApi.Data.GetTiers.execute() }
            val seasonDF = ioAsync { EternalReturnDakGGApi.Data.GetCurrentSeason.execute() }
            val infusionsDF = ioAsync { EternalReturnDakGGApi.Data.GetInfusions.execute() }

            val games = gamesDF.await()
            val characters = charactersDF.await()
            val tiers = tiersDF.await()
            val season = seasonDF.await()
            val infusions = infusionsDF.await()

            ioLaunch {
                resourcesDownloadService.gameDataDownload(
                    games = games.matches,
                    characterResponse = characters,
                    tiers = tiers,
                    infusionsResponse = infusions,
                    bannerFallbackSeasonId = season.id,
                )
                log.debug { "gameDataDownload 预备请求数据已完成" }
            }
            ioLaunch {
                resourcesDownloadService.downloadProfileData(profile)
                log.debug { "downloadProfileData 预备请求数据已完成" }
            }

            PreheatedData(profile, games.matches, characters, tiers, season, infusions)
        }
    }
}
