package cn.luorenmu.plugins.character

import cn.luorenmu.nutdraw.dom.NutImage
import cn.luorenmu.nutdraw.dom.NutText
import cn.luorenmu.nutdraw.layout.FlexLayoutEngine
import cn.luorenmu.nutdraw.layout.LayoutBox
import cn.luorenmu.nutdraw.NutDraw
import cn.luorenmu.service.entity.CharacterStats
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 *
 * @author LoMu
 * Date 2026/8/16 15:30
 */
class CharacterStatsTemplateRegressionTest {
    @Test
    fun `compact grid renders every hero weapon combination`() {
        val data = characterStats(23)
        val document = CharacterStatsTemplate().build(data)
        val layout = FlexLayoutEngine().layout(document.root, document.width.toFloat(), document.height.toFloat())

        repeat(23) { index -> assertNotNull(layout.findById("stats-card-$index")) }
        assertTrue(document.height < 600)
    }

    @Test
    fun `card shows requested fields with play count below pick rate`() {
        val document = CharacterStatsTemplate().build(characterStats(1))
        val layout = FlexLayoutEngine().layout(document.root, document.width.toFloat(), document.height.toFloat())
        val character = assertNotNull(layout.findById("stats-character-0"))
        val weapon = assertNotNull(layout.findById("stats-weapon-0"))
        val tier = assertNotNull(layout.findById("stats-tier-0"))
        val pickRate = assertNotNull(layout.findById("stats-pick-rate-0"))
        val playCount = assertNotNull(layout.findById("stats-play-count-0"))

        assertTrue((character.node as NutImage).source?.endsWith("/character.png") == true)
        assertTrue((weapon.node as NutImage).source?.endsWith("/weapon.png") == true)
        assertTrue(tier.node.style.backgroundImage?.endsWith("/character-tier-A.svg") == true)
        assertTrue(tier.node !is NutImage)
        assertEquals("选择率 12.50%", (pickRate.node as NutText).value)
        assertEquals("125 场", (playCount.node as NutText).value)
        assertTrue(playCount.bounds.top >= pickRate.bounds.bottom)
    }

    @Test
    fun `selection rate uses weapon games divided by total games`() {
        assertEquals("12.50%", selectionRate(playCount = 125, totalGames = 1_000))
        assertEquals("0.0%", selectionRate(playCount = 0, totalGames = 1_000))
        assertEquals("0.0%", selectionRate(playCount = 10, totalGames = 0))
    }

    @Test
    fun `compact grid renders to png`() = runBlocking {
        val output = Path.of(System.getProperty("user.dir"), "build", "previews", "character-stats-compact.png")
        Files.createDirectories(output.parent)

        NutDraw.render(CharacterStatsTemplate(), characterStats(43), output)

        assertTrue(Files.size(output) > 100)
    }

    private fun characterStats(size: Int) = CharacterStats(
        tier = "灭钻",
        players = List(size) {
            CharacterStats.CharacterStatsPlayer(
                characterImgUrl = "/character.png",
                weaponImgUrl = "/weapon.png",
                tier = "A",
                pickRate = "12.50%",
                playCount = 125,
            )
        },
        httpServer = "",
    )

    private fun LayoutBox.findById(id: String): LayoutBox? =
        takeIf { node.id == id } ?: children.firstNotNullOfOrNull { it.findById(id) }
}
