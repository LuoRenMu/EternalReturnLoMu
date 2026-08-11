package cn.luorenmu.nutdraw

import cn.luorenmu.nutdraw.dom.NutElement
import cn.luorenmu.nutdraw.dom.NutNode
import cn.luorenmu.nutdraw.dom.NutText
import cn.luorenmu.nutdraw.layout.FlexLayoutEngine
import cn.luorenmu.nutdraw.layout.LayoutBox
import cn.luorenmu.nutdraw.templates.SearchPlayerTemplate
import cn.luorenmu.service.entity.EternalReturnPlayRender
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SearchPlayerTemplateRegressionTest {
    @Test
    fun `rank chart keeps spacing below overview data`() {
        val document = SearchPlayerTemplate().build(player(matchType = "排位"))
        val layout = FlexLayoutEngine().layout(document.root, document.width.toFloat(), document.height.toFloat())
        val overview = assertNotNull(layout.findById("rank_overview"))
        val chart = assertNotNull(layout.findById("rank_stats"))

        assertTrue(chart.bounds.top - overview.bounds.bottom >= 10f)
    }

    @Test
    fun `cobalt match shows infusions instead of kda`() {
        val root = SearchPlayerTemplate().build(player(matchType = "钴协议")).root
        val texts = root.textValues()

        assertNotNull(root.findById("infusions-game-1"))
        assertTrue("灌注" in texts)
        assertTrue("KDA" !in texts)
    }

    private fun player(matchType: String) = EternalReturnPlayRender(
        nickName = "神圣审判",
        level = 206,
        httpServer = "",
        data = EternalReturnPlayRender.EternalReturnPlayerData(
            rp = "6800", rpName = "半神", play = 8, avgTk = "9.75", avgKill = "2.75",
            avgRank = "#5.00", avgAssists = "3.88", avgDmg = "10508",
        ),
        recentPlayers = emptyList(),
        characterUseStats = emptyList(),
        summary = null,
        mmrStats = EternalReturnPlayRender.EternalReturnPlayerMMRStats(
            listOf("8/04", "8/05", "8/06"), listOf(6400, 6600, 6800),
        ),
        matches = listOf(
            EternalReturnPlayRender.EternalReturnPlayerMatchData(
                gameId = "game-1",
                type = matchType,
                tk = 12,
                kill = 4,
                assist = 8,
                infusions = listOf(
                    EternalReturnPlayRender.EternalReturnPlayerMatchData.EternalReturnPlayerInfusion("/infusion-1.png", 2),
                    EternalReturnPlayRender.EternalReturnPlayerMatchData.EternalReturnPlayerInfusion("/infusion-2.png", 1),
                    EternalReturnPlayRender.EternalReturnPlayerMatchData.EternalReturnPlayerInfusion(),
                ),
            )
        ),
        season = "季前赛 12",
        mode = "排位",
    )

    private fun LayoutBox.findById(id: String): LayoutBox? =
        takeIf { node.id == id } ?: children.firstNotNullOfOrNull { it.findById(id) }

    private fun NutNode.textValues(): List<String> = when (this) {
        is NutText -> listOf(value)
        is NutElement -> children.flatMap { it.textValues() }
        else -> emptyList()
    }
}
