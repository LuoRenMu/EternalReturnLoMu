package cn.luorenmu.plugins.character

import cn.luorenmu.nutdraw.NutDraw
import cn.luorenmu.nutdraw.render.ImageDecoder
import cn.luorenmu.nutdraw.render.LoadedImage
import cn.luorenmu.nutdraw.layout.FlexLayoutEngine
import cn.luorenmu.nutdraw.layout.LayoutBox
import cn.luorenmu.request.api.entity.response.dakgg.DakGGCharacterStatsResponse
import cn.luorenmu.request.api.entity.response.dakgg.DakGGCharactersResponse
import cn.luorenmu.request.api.impl.EternalReturnDakGGApi
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

/** Renders captured public API snapshots against real local assets, opt-in via CHARACTER_STATS_FIXTURE_ROOT. */
class RealCharacterStatsPreviewTest {
    @Test fun `all captured characters weapons and grades render without dropped or missing images`() = runBlocking {
        val rootPath = System.getenv("CHARACTER_STATS_FIXTURE_ROOT")
        assumeTrue(!rootPath.isNullOrBlank())
        val root = Path.of(assertNotNull(rootPath))
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        val characters = json.decodeFromString<DakGGCharactersResponse>(Files.readString(root.resolve("build/character-metadata-live-20261001.json")))
        val snapshot = json.decodeFromString<DakGGCharacterStatsResponse>(Files.readString(root.resolve("build/character-stats-live-20261001.json")))
        val stats = CharacterStatsCollector(loadCharacters = { characters }, loadStatistics = { snapshot }).collect()
            .copy(httpServer = root.toUri().toASCIIString().trimEnd('/'))
        val expected = characters.characters.flatMap { character -> character.weaponTypes.map { character.id to it.id } }.toSet()
        assertEquals(expected.size, stats.players.size)
        assertEquals(setOf("S", "A", "B", "C", "D"), stats.players.map { it.tier }.filter(String::isNotBlank).toSet())
        stats.players.forEach { player ->
            assertTrue(Files.isRegularFile(root.resolve(player.characterImgUrl.removePrefix("/"))), player.characterImgUrl)
            assertTrue(Files.isRegularFile(root.resolve(player.weaponImgUrl.removePrefix("/"))), player.weaponImgUrl)
        }
        val imagePaths = stats.players.flatMap { listOf(it.characterImgUrl, it.weaponImgUrl) }.distinct()
        imagePaths.forEach { path ->
            org.jetbrains.skia.Image.makeFromEncoded(Files.readAllBytes(root.resolve(path.removePrefix("/")))).use { image ->
                assertTrue(image.width > 0 && image.height > 0, path)
            }
        }
        CHARACTER_TIER_GRADES.forEach { grade ->
            val path = root.resolve("resources/images/character/tier/character-tier-$grade.svg")
            ImageDecoder().decode(path.toUri().toASCIIString(), Files.readAllBytes(path)).use { image ->
                assertIs<LoadedImage.Vector>(image)
            }
        }
        val document = CharacterStatsTemplate().build(stats)
        val layout = FlexLayoutEngine().layout(document.root, document.width.toFloat(), document.height.toFloat())
        stats.players.indices.forEach { index ->
            val card = assertNotNull(layout.findById("stats-card-$index"))
            assertTrue(card.bounds.bottom <= document.height)
            assertNotNull(layout.findById("stats-character-$index"))
            assertNotNull(layout.findById("stats-weapon-$index"))
        }
        val output = root.resolve("build/character-stats-all-live.png")
        NutDraw.render(CharacterStatsTemplate(), stats, output)
        assertTrue(Files.size(output) > 100_000)
        println("realCharacterStatsPreview combinations=${stats.players.size} pending=${stats.players.count { it.winRate == NO_CHARACTER_STATISTICS }} decodedImages=${imagePaths.size} file=$output")
    }

    @Test fun `live fresh collector metadata can be read again for resource preheat`() = runBlocking {
        assumeTrue(!System.getenv("CHARACTER_STATS_FIXTURE_ROOT").isNullOrBlank())
        val data = CharacterStatsCollector().collect()
        val metadata = EternalReturnDakGGApi.Data.GetCharacters.execute()
        val combinations = metadata.characters.flatMap { c -> c.weaponTypes.map { c.id to it.id } }.toSet()
        assertTrue(data.players.size >= combinations.size)
        assertTrue(data.players.map { it.characterImgUrl to it.weaponImgUrl }.distinct().size == data.players.size)
        println("realCharacterStatsProvider characters=${metadata.characters.size} combinations=${data.players.size} metadataReReadable=true")
    }

    private fun LayoutBox.findById(id: String): LayoutBox? =
        takeIf { node.id == id } ?: children.firstNotNullOfOrNull { it.findById(id) }
}
