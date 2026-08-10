package cn.luorenmu.nutdraw

import cn.luorenmu.request.api.impl.EternalReturnDakGGApi
import cn.luorenmu.request.entity.module.MatchingMode
import cn.luorenmu.service.PlayerRenderAssembler
import kotlinx.coroutines.async
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
            val games = async { EternalReturnDakGGApi.Game.GetGame(nickname).execute() }
            val profile = async { EternalReturnDakGGApi.User.GetProfile(nickname).execute() }
            val characters = async { EternalReturnDakGGApi.Data.GetCharacters.execute() }
            val tiers = async { EternalReturnDakGGApi.Data.GetTiers.execute() }
            val season = async { EternalReturnDakGGApi.Data.GetCurrentSeason.execute() }
            val infusions = async { EternalReturnDakGGApi.Data.GetInfusions.execute() }
            PlayerRenderAssembler().assemble(
                profile.await(), games.await().matches, characters.await(), tiers.await(), season.await(), infusions.await(), MatchingMode.Rank, nickname,
            )
                .also { sync.cancel() }
        }
        val queryMs = (System.nanoTime() - queryStarted) / 1_000_000
        val output = Path.of(System.getProperty("user.dir"), "build", "previews", "search-player-神圣审判.png")
        Files.createDirectories(output.parent)
        val renderer = NutDrawBotImageRenderer()
        val renderTimes = (1..5).map { iteration ->
            val started = System.nanoTime()
            renderer.renderSearchPlayer(renderData, output)
            val elapsed = (System.nanoTime() - started) / 1_000_000
            println("[PLAYER-BENCH] nutdraw-render-$iteration=${elapsed}ms")
            elapsed
        }
        assertTrue(Files.size(output) > 100)
        println("[PLAYER-BENCH] parallel-query-and-assemble=${queryMs}ms warm-render-median=${renderTimes.drop(1).sorted()[1]}ms total=${(System.nanoTime() - totalStarted) / 1_000_000}ms")
        println("Real player preview retained at: $output")
    }
}
