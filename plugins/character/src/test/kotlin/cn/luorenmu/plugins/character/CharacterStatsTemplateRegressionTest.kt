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
    fun `card shows win rate with average damage below it`() {
        val document = CharacterStatsTemplate().build(characterStats(1))
        val layout = FlexLayoutEngine().layout(document.root, document.width.toFloat(), document.height.toFloat())
        val character = assertNotNull(layout.findById("stats-character-0"))
        val weapon = assertNotNull(layout.findById("stats-weapon-0"))
        val tier = assertNotNull(layout.findById("stats-tier-0"))
        val rank = assertNotNull(layout.findById("stats-rank-0"))
        val winRate = assertNotNull(layout.findById("stats-win-rate-0"))
        val damage = assertNotNull(layout.findById("stats-damage-0"))

        assertTrue((character.node as NutImage).source?.endsWith("/character.png") == true)
        assertTrue((weapon.node as NutImage).source?.endsWith("/weapon.png") == true)
        assertTrue(tier.node.style.backgroundImage?.endsWith("/character-tier-A.svg") == true)
        assertTrue(tier.node !is NutImage)
        assertEquals("#1", (rank.node as NutText).value)
        assertEquals("胜率 20.00%", (winRate.node as NutText).value)
        assertEquals("伤害 12,345", (damage.node as NutText).value)
        assertTrue(damage.bounds.top >= winRate.bounds.bottom)
    }

    @Test
    fun `portrait badges follow display order regardless of api rank`() {
        val data = characterStats(120).let { stats ->
            stats.copy(players = stats.players.mapIndexed { index, player ->
                player.copy(rank = if (index % 2 == 0) 999 else 0)
            })
        }
        val document = CharacterStatsTemplate().build(data)
        val layout = FlexLayoutEngine().layout(document.root, document.width.toFloat(), document.height.toFloat())

        data.players.indices.forEach { index ->
            val portrait = assertNotNull(layout.findById("stats-portrait-$index"))
            val badge = assertNotNull(layout.findById("stats-rank-$index"))
            assertEquals("#${index + 1}", (badge.node as NutText).value)
            assertEquals(8f, badge.node.style.fontSize)
            assertTrue(badge.bounds.left >= portrait.bounds.left)
            assertTrue(badge.bounds.top >= portrait.bounds.top)
            assertTrue(badge.bounds.right <= portrait.bounds.right)
            assertTrue(badge.bounds.bottom <= portrait.bounds.bottom)
        }
    }

    @Test
    fun `win rate and damage use weapon game count`() {
        assertEquals("20.00%", winRate(wins = 25, games = 125))
        assertEquals("0.0%", winRate(wins = 0, games = 125))
        assertEquals("0.0%", winRate(wins = 10, games = 0))
        assertEquals(12_345, averageDamage(totalDamage = 1_543_125, games = 125))
        assertEquals(0, averageDamage(totalDamage = 10_000, games = 0))
    }

    @Test
    fun `compact grid renders to png`() = runBlocking {
        val output = Path.of(System.getProperty("user.dir"), "build", "previews", "character-stats-compact.png")
        Files.createDirectories(output.parent)

        NutDraw.render(CharacterStatsTemplate(), characterStats(43), output)

        assertTrue(Files.size(output) > 100)
    }

    @Test
    fun `missing statistics keeps portrait and weapon without fake D grade or zero metrics`() {
        val data = characterStats(1).let { stats ->
            stats.copy(players = stats.players.map { it.copy(tier = "", winRate = NO_CHARACTER_STATISTICS, averageDamage = 0) })
        }
        val document = CharacterStatsTemplate().build(data)
        val layout = FlexLayoutEngine().layout(document.root, document.width.toFloat(), document.height.toFloat())
        assertNotNull(layout.findById("stats-character-0"))
        assertNotNull(layout.findById("stats-weapon-0"))
        assertEquals(null, assertNotNull(layout.findById("stats-tier-0")).node.style.backgroundImage)
        assertEquals("暂无数据", (assertNotNull(layout.findById("stats-win-rate-0")).node as NutText).value)
        assertEquals("伤害 —", (assertNotNull(layout.findById("stats-damage-0")).node as NutText).value)
        assertEquals(11f, assertNotNull(layout.findById("stats-win-rate-0")).node.style.fontSize)
    }

    private fun characterStats(size: Int) = CharacterStats(
        tier = "灭钻",
        players = List(size) {
            CharacterStats.CharacterStatsPlayer(
                characterImgUrl = "/character.png",
                weaponImgUrl = "/weapon.png",
                tier = "A",
                rank = 3,
                rankSize = 43,
                winRate = "20.00%",
                averageDamage = 12_345,
            )
        },
        httpServer = "",
    )

    private fun LayoutBox.findById(id: String): LayoutBox? =
        takeIf { node.id == id } ?: children.firstNotNullOfOrNull { it.findById(id) }
}
