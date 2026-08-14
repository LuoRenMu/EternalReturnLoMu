package cn.luorenmu.plugins.player

import cn.luorenmu.exception.MessageReplyException
import cn.luorenmu.common.util.toPath
import cn.luorenmu.request.api.impl.EternalReturnDakGGApi
import cn.luorenmu.request.entity.module.MatchingMode
import cn.luorenmu.service.ResourcesDownloadService
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

/** Opt-in integration preview: REAL_PLAYER_PREVIEW=true (uses live DAK.GG data). */
class RealPlayerPreviewTest {
    @Test
    fun renderSacredJudgement() = runBlocking {
        if (System.getenv("REAL_PLAYER_PREVIEW") != "true") return@runBlocking
        val nickname = "神圣审判"
        val totalStarted = System.nanoTime()
        val queryStarted = System.nanoTime()
        val renderData = coroutineScope {
            val sync = async { EternalReturnDakGGApi.User.Sync(nickname).execute() }
            val profile = EternalReturnDakGGApi.User.GetProfile(nickname).execute()
            val latestSeasonId = profile.playerSeasons?.firstOrNull()?.seasonId
                ?: throw MessageReplyException("该玩家无任何赛季有游玩数据")
            val seasons = async { EternalReturnDakGGApi.Data.GetGameDataBySeason.execute() }
            val games = async {
                EternalReturnDakGGApi.Game.GetGame(nickname, seasons.await().getSeasonById(latestSeasonId).key).execute()
            }
            val characters = async { EternalReturnDakGGApi.Data.GetCharacters.execute() }
            val tiers = async { EternalReturnDakGGApi.Data.GetTiers.execute() }
            val season = async { EternalReturnDakGGApi.Data.GetCurrentSeason.execute() }
            val infusions = async { EternalReturnDakGGApi.Data.GetInfusions.execute() }
            val resolvedGames = games.await().matches
            val resolvedCharacters = characters.await()
            val resolvedTiers = tiers.await()
            val resolvedSeason = season.await()
            val resolvedInfusions = infusions.await()
            val downloader = ResourcesDownloadService()
            coroutineScope {
                listOf(
                    async { downloader.downloadProfileData(profile) },
                    async {
                        downloader.gameDataDownload(
                            resolvedGames,
                            resolvedCharacters,
                            resolvedTiers,
                            resolvedInfusions,
                            bannerFallbackSeasonId = resolvedSeason.id,
                        )
                    },
                ).awaitAll()
            }
            PlayerRenderAssembler().assemble(
                profile, resolvedGames, resolvedCharacters, resolvedTiers, resolvedSeason, resolvedInfusions, MatchingMode.Rank, nickname,
            )
                .also { sync.cancel() }
        }
        assertTrue(renderData.profileImageUrl?.toPath()?.let(Files::isRegularFile) == true)
        assertTrue(Files.isRegularFile(renderData.bannerUrl.toPath()))
        val queryMs = (System.nanoTime() - queryStarted) / 1_000_000
        val output = Path.of(System.getProperty("user.dir"), "build", "previews", "search-player-神圣审判.png")
        Files.createDirectories(output.parent)
        val renderTimes = (1..5).map { iteration ->
            val started = System.nanoTime()
            cn.luorenmu.nutdraw.NutDraw.render(SearchPlayerTemplate(), renderData, output)
            val elapsed = (System.nanoTime() - started) / 1_000_000
            println("[PLAYER-BENCH] nutdraw-render-$iteration=${elapsed}ms")
            elapsed
        }
        assertTrue(Files.size(output) > 100)
        println("[PLAYER-BENCH] parallel-query-and-assemble=${queryMs}ms warm-render-median=${renderTimes.drop(1).sorted()[1]}ms total=${(System.nanoTime() - totalStarted) / 1_000_000}ms")
        println("Real player preview retained at: $output")
    }
}
