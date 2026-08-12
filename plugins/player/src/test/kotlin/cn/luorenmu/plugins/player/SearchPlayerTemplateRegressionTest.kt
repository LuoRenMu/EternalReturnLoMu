package cn.luorenmu.plugins.player

import cn.luorenmu.nutdraw.css.VerticalAlign
import cn.luorenmu.nutdraw.dom.NutElement
import cn.luorenmu.nutdraw.dom.NutImage
import cn.luorenmu.nutdraw.dom.NutNode
import cn.luorenmu.nutdraw.dom.NutText
import cn.luorenmu.nutdraw.layout.FlexLayoutEngine
import cn.luorenmu.nutdraw.layout.LayoutBox
import cn.luorenmu.service.entity.EternalReturnPlayRender
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchPlayerTemplateRegressionTest {
    @Test
    fun `summary content is vertically centered in panel`() {
        val data = player(matchType = "排位").copy(
            summary = EternalReturnPlayRender.EternalReturnSummary(
                count = 5,
                avgRank = "2.4",
                wins = "2",
                avgTk = "7.2",
                ranks = listOf(1, 2, 4, 3, 2),
                avgDmg = "10420.0",
            )
        )
        val document = SearchPlayerTemplate().build(data)
        val layout = FlexLayoutEngine().layout(document.root, document.width.toFloat(), document.height.toFloat())
        val panel = assertNotNull(layout.findById("summary-panel"))
        val title = assertNotNull(layout.findById("summary-title"))
        val ranks = assertNotNull(layout.findById("summary-ranks"))

        assertEquals(title.bounds.top - panel.bounds.top, panel.bounds.bottom - ranks.bounds.bottom)
    }

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

    @Test
    fun `header corners and recent-player table share aligned layout`() {
        val document = SearchPlayerTemplate().build(player(matchType = "排位"))
        val layout = FlexLayoutEngine().layout(document.root, document.width.toFloat(), document.height.toFloat())

        val banner = assertNotNull(layout.findById("header-banner"))
        val profile = assertNotNull(layout.findById("profile-image"))
        assertEquals(10f, banner.node.style.cornerRadii?.topLeft)
        assertEquals(10f, banner.node.style.cornerRadii?.topRight)
        assertEquals(10f, profile.node.style.cornerRadii?.topLeft)

        val nameHeader = assertNotNull(layout.findById("recent-name-header"))
        val recentHeaderRow = assertNotNull(layout.findById("recent-header-row"))
        val nameCell = assertNotNull(layout.findById("recent-name-0"))
        val characterNameCell = assertNotNull(layout.findById("character-name-0"))
        val winRateHeader = assertNotNull(layout.findById("recent-win-rate-header"))
        val winRateCell = assertNotNull(layout.findById("recent-win-rate-0"))
        assertEquals(nameCell.bounds.left, nameHeader.bounds.left)
        assertEquals(winRateCell.bounds.left, winRateHeader.bounds.left)
        assertEquals(
            (recentHeaderRow.bounds.top + recentHeaderRow.bounds.bottom) / 2f,
            (nameHeader.bounds.top + nameHeader.bounds.bottom) / 2f,
        )
        assertEquals(10f, recentHeaderRow.node.style.cornerRadii?.topLeft)
        assertEquals(10f, recentHeaderRow.node.style.cornerRadii?.topRight)
        assertTrue("Friend" in nameCell.node.textValues())
        assertTrue("3 场游戏" in nameCell.node.textValues())
        assertTrue("Jackie" in characterNameCell.node.textValues())
        assertTrue("3 场游戏(33.3%)" in characterNameCell.node.textValues())
        assertEquals(nameCell.node.style.height, characterNameCell.node.style.height)
        assertEquals(nameCell.node.style.justifyContent, characterNameCell.node.style.justifyContent)
        assertNotNull((assertNotNull(layout.findById("recent-avatar-0")).node as NutImage).source)
        val match = assertNotNull(layout.findById("match-game-1"))
        assertTrue(match.bounds.left < document.width)
        assertTrue(match.bounds.right <= document.width)
    }

    private fun player(matchType: String) = EternalReturnPlayRender(
        nickName = "神圣审判",
        level = 206,
        httpServer = "",
        data = EternalReturnPlayRender.EternalReturnPlayerData(
            rp = "6800", rpName = "半神", play = 8, avgTk = "9.75", avgKill = "2.75",
            avgRank = "#5.00", avgAssists = "3.88", avgDmg = "10508",
        ),
        recentPlayers = listOf(
            EternalReturnPlayRender.EternalReturnPlayerRecentPlay(
                imageWrapperUrl = "/resources/images/character/1/CharProfile/1001003.png",
                plays = 3,
                winRate = "33.3%",
                avgRank = "#2.0",
                nickname = "Friend",
            )
        ),
        characterUseStats = listOf(
            EternalReturnPlayRender.EternalReturnCharacterUseStats(
                imgUrl = "/resources/images/character/1/CharProfile/1001003.png",
                characterName = "Jackie",
                characterPlay = 3,
                winRate = "33.3%",
                getRP = 10,
                avgRank = "#2.0",
                avgDmg = 1000,
            )
        ),
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
