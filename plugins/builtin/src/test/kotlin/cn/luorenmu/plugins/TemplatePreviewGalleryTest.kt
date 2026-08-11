package cn.luorenmu.plugins

import cn.luorenmu.command.entity.CommandHelp
import cn.luorenmu.command.entity.CommandOptional
import cn.luorenmu.command.template.HelpTemplate
import cn.luorenmu.nutdraw.NutDraw
import cn.luorenmu.plugins.character.CharacterDetailTemplate
import cn.luorenmu.plugins.character.CharacterStatsTemplate
import cn.luorenmu.plugins.player.SearchPlayerTemplate
import cn.luorenmu.plugins.tier.TierStatisticsTemplate
import cn.luorenmu.request.api.entity.response.dakgg.DakGGLeaderboardResponse
import cn.luorenmu.service.entity.CharacterStats
import cn.luorenmu.service.entity.EternalReturnEquip
import cn.luorenmu.service.entity.EternalReturnPlayRender
import cn.luorenmu.service.entity.TierStatistics
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class TemplatePreviewGalleryTest {
    private val outputDir: Path = Path.of(System.getProperty("user.dir"), "build", "previews")

    @Test
    fun renderAllTemplatePreviews() = runBlocking {
        Files.createDirectories(outputDir)

        NutDraw.render(HelpTemplate(),
            CommandHelp(
                listOf(
                    CommandHelp.CommandHelpItem("help", "查看机器人支持的全部命令", "/help", emptyList()),
                    CommandHelp.CommandHelpItem("player", "查询玩家段位与最近比赛", "/player LuoMu", listOf(CommandOptional("nickname", "玩家昵称"))),
                    CommandHelp.CommandHelpItem("character", "查看角色统计和推荐出装", "/character Aya", listOf(CommandOptional("name"), CommandOptional("tier", required = false))),
                )
            ),
            preview("help-skia.png"),
        )

        NutDraw.render(TierStatisticsTemplate(),
            TierStatistics(
                season = "Season 10 Ranked",
                tierTypes = listOf("diamond", "platinum", "gold", "silver", "bronze", "iron"),
                count = mapOf("diamond" to 842, "platinum" to 2310, "gold" to 4890, "silver" to 3620, "bronze" to 1700, "iron" to 530),
                rate = mapOf("diamond" to "6.1", "platinum" to "16.6", "gold" to "35.2", "silver" to "26.1", "bronze" to "12.2", "iron" to "3.8"),
                eternal = DakGGLeaderboardResponse.Cutoffs(mmr = 7800),
                demigod = DakGGLeaderboardResponse.Cutoffs(mmr = 6500),
                date = "2026-08-10 21:45:00",
            ),
            preview("tier-statistics-skia.png"),
        )

        NutDraw.render(CharacterStatsTemplate(),
            CharacterStats(
                totalGames = 182430,
                totalPlayers = 34210,
                tierName = "钻石及以上",
                tier = "Diamond+",
                players = listOf(
                    statsPlayer(1, "妮琪", "S", "5230", "18.4%", "12.8%"),
                    statsPlayer(2, "艾玛", "S", "5102", "17.1%", "11.9%"),
                    statsPlayer(3, "阿雅", "A", "4870", "14.6%", "10.7%"),
                    statsPlayer(4, "彰一", "A", "4625", "13.2%", "9.8%"),
                ),
            ),
            preview("character-stats-skia.png"),
        )

        NutDraw.render(CharacterDetailTemplate(),
            RioCharacterDetailSample.create(),
            preview("character-detail-skia.png"),
        )

        NutDraw.render(SearchPlayerTemplate(), playerPreview(), preview("search-player-skia.png"))

        listOf("help-skia.png", "tier-statistics-skia.png", "character-stats-skia.png", "character-detail-skia.png", "search-player-skia.png")
            .forEach { assertTrue(Files.size(preview(it)) > 100, "$it should be a non-empty PNG") }
    }

    private fun statsPlayer(rank: Int, name: String, tier: String, rp: String, pick: String, win: String) =
        CharacterStats.CharacterStatsPlayer(rank, "", "", name, tier, rp, 1200 - rank * 90, win, "3.2", "42.5%", pick, "3.8", "18420", "90")

    private fun playerPreview() = EternalReturnPlayRender(
        nickName = "LuoMu",
        level = 126,
        data = EternalReturnPlayRender.EternalReturnPlayerData(rp = "6842", rpName = "Demigod", play = 326, avgTk = "7.4", avgKill = "3.1", avgRank = "3.7", avgDmg = "21,540"),
        recentPlayers = emptyList(),
        characterUseStats = emptyList(),
        summary = null,
        mmrStats = EternalReturnPlayRender.EternalReturnPlayerMMRStats(
            mmrDate = listOf("8/04", "8/05", "8/06", "8/07", "8/08", "8/09", "8/10"),
            mmr = listOf(6400, 6510, 6460, 6640, 6720, 6690, 6842),
        ),
        season = "Season 10",
        mode = "排位",
        matches = listOf(
            match(1, "阿雅", 12, 6, 8, 31540, 28),
            match(3, "妮琪", 8, 3, 11, 24210, 12),
            match(
                2, "艾玛", 5, 2, 6, 18760, -9, type = "钴协议",
                infusions = listOf(
                    EternalReturnPlayRender.EternalReturnPlayerMatchData.EternalReturnPlayerInfusion("/resources/images/trait/skill/7000201.png", 3),
                    EternalReturnPlayRender.EternalReturnPlayerMatchData.EternalReturnPlayerInfusion("/resources/images/trait/skill/7010501.png", 2),
                    EternalReturnPlayRender.EternalReturnPlayerMatchData.EternalReturnPlayerInfusion("/resources/images/trait/skill/7310401.png", 1),
                ),
            ),
        ),
    )

    private fun match(
        rank: Int,
        character: String,
        tk: Int,
        kill: Int,
        assist: Int,
        damage: Long,
        change: Int,
        type: String = "排位",
        infusions: List<EternalReturnPlayRender.EternalReturnPlayerMatchData.EternalReturnPlayerInfusion>? = null,
    ) =
        EternalReturnPlayRender.EternalReturnPlayerMatchData(
            rank = rank, characterName = character, type = type, dateMonth = "8月10日", dateHour = "20:30",
            tk = tk, kill = kill, assist = assist, dmg = damage, rp = 6800, rpChange = change,
            equips = MutableList(5) { EternalReturnEquip("", "") }, gameId = "123456", version = "1.42.0",
            infusions = infusions,
        )

    private fun preview(name: String) = outputDir.resolve(name)
}
