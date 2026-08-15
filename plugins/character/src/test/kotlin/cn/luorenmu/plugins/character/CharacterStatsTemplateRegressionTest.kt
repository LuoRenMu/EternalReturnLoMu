package cn.luorenmu.plugins.character

import cn.luorenmu.nutdraw.dom.NutImage
import cn.luorenmu.nutdraw.layout.FlexLayoutEngine
import cn.luorenmu.nutdraw.layout.LayoutBox
import cn.luorenmu.service.entity.CharacterStats
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CharacterStatsTemplateRegressionTest {
    @Test
    fun `tier image appears before character name`() {
        val data = CharacterStats(
            totalGames = 100,
            totalPlayers = 10,
            tierName = "diamond_plus",
            tier = "钻石+",
            players = listOf(
                CharacterStats.CharacterStatsPlayer(
                    rank = 1,
                    characterImgUrl = "",
                    weaponImgUrl = "",
                    characterName = "测试角色",
                    tier = "A",
                    rp = "1",
                    playCount = 10,
                    winRate = "50.0%",
                    avgKill = "1.0",
                    top3Rate = "60.0%",
                    pick = "10.0%",
                    avgRank = "3.0",
                    avgDmg = "1000",
                    relativeWinRate = "100.0",
                )
            ),
            httpServer = "",
        )
        val document = CharacterStatsTemplate().build(data)
        val layout = FlexLayoutEngine().layout(document.root, document.width.toFloat(), document.height.toFloat())
        val tierIcon = assertNotNull(layout.findById("stats-tier-icon-1"))
        val characterName = assertNotNull(layout.findById("stats-character-name-1"))

        assertTrue((tierIcon.node as NutImage).source?.endsWith("/character-tier-A.svg") == true)
        assertTrue(tierIcon.bounds.right <= characterName.bounds.left)
        assertTrue(tierIcon.bounds.right - tierIcon.bounds.left <= 24f)
    }

    private fun LayoutBox.findById(id: String): LayoutBox? =
        takeIf { node.id == id } ?: children.firstNotNullOfOrNull { it.findById(id) }
}
