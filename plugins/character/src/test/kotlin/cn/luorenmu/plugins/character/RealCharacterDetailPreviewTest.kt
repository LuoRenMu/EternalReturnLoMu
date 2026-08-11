package cn.luorenmu.plugins.character

import cn.luorenmu.service.CharacterDetailCollector
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

/** Opt-in live visual test: REAL_CHARACTER_PREVIEW=true. */
class RealCharacterDetailPreviewTest {
    @Test
    fun renderAyaDetail() = runBlocking {
        if (System.getenv("REAL_CHARACTER_PREVIEW") != "true") return@runBlocking
        val detail = CharacterDetailCollector().collect("1", "SQUAD", "RANK", "diamond_plus")
        val output = Path.of(System.getProperty("user.dir"), "build", "previews", "character-detail-real-skia.png")
        Files.createDirectories(output.parent)
        cn.luorenmu.nutdraw.NutDraw.render(CharacterDetailTemplate(), detail, output)
        assertTrue(Files.size(output) > 100)
        println("Real character detail preview retained at: $output")
    }
}
