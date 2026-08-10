package cn.luorenmu.nutdraw

import cn.luorenmu.request.entity.module.DakGGServerName
import cn.luorenmu.service.CharacterStatsCollector
import cn.luorenmu.service.TierStatisticsCollector
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

/** Opt-in live previews: REAL_STATISTICS_PREVIEW=true. */
class RealStatisticsPreviewTest {
    @Test
    fun renderRealTierAndCharacterStatistics() = runBlocking {
        if (System.getenv("REAL_STATISTICS_PREVIEW") != "true") return@runBlocking
        val (tiers, characters) = coroutineScope {
            val tierData = async { TierStatisticsCollector().collect(DakGGServerName.Asia) }
            val characterData = async { CharacterStatsCollector().collect(tierOrCharacter = "s") }
            tierData.await() to characterData.await()
        }
        val outputDir = Path.of(System.getProperty("user.dir"), "build", "previews")
        Files.createDirectories(outputDir)
        val tierOutput = outputDir.resolve("tier-statistics-real-skia.png")
        val characterOutput = outputDir.resolve("character-stats-real-skia.png")
        val renderer = NutDrawBotImageRenderer()
        renderer.renderTierStatistics(tiers, tierOutput)
        renderer.renderCharacterStats(characters, characterOutput)
        assertTrue(Files.size(tierOutput) > 100)
        assertTrue(Files.size(characterOutput) > 100)
    }
}
