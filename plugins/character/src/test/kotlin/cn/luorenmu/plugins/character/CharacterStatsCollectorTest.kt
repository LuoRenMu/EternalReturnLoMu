package cn.luorenmu.plugins.character

import cn.luorenmu.exception.MessageReplyException
import cn.luorenmu.request.api.entity.response.dakgg.DakGGCharacterStatsResponse
import cn.luorenmu.request.api.entity.response.dakgg.DakGGCharacterStatsResponse.*
import cn.luorenmu.request.api.entity.response.dakgg.DakGGCharactersResponse
import cn.luorenmu.request.api.entity.response.dakgg.DakGGCharactersResponse.DakGGCharacterById
import cn.luorenmu.request.entity.module.DakGGRank
import kotlinx.coroutines.runBlocking
import java.time.Duration
import kotlin.test.*

class CharacterStatsCollectorTest {
    private fun character(id: Long, vararg weapons: Long) = DakGGCharacterById(
        id = id, name = "角色$id", weaponTypes = weapons.map { DakGGCharacterById.DakGGWeaponType(id = it) },
        skins = listOf(DakGGCharacterById.DakGGSkin(id = 1000 + id)),
    )

    private fun weapon(id: Int, grade: String = "A", score: Double? = 3.0, games: Int = 100) = WeaponStat(
        key = id, count = games, win = 25, top3 = 40, place = 300, playerKill = 100, playerAssistant = 200,
        playerDeaths = 50, damageToPlayer = 1_000_000, damageToMonster = 5_000_000,
        monsterKill = 300, teamKill = 300, mmrGain = 100, tier = grade, tierScore = score, viewContribution = 100,
    )

    private fun snapshot(vararg rows: CharacterStat) = DakGGCharacterStatsResponse(CharacterStatSnapshot(
        dt = 7, patch = 12050, matchingMode = 3, teamMode = 3, tier = "diamond_plus", tierCount = 100,
        tierGameCount = 1000, characterStats = rows.toList(),
    ))

    private fun collector(characters: List<DakGGCharacterById>, stats: DakGGCharacterStatsResponse) =
        CharacterStatsCollector(loadCharacters = { DakGGCharactersResponse(ArrayList(characters)) }, loadStatistics = { stats })

    @Test fun `partial snapshot still shows all characters and supported weapons`() = runBlocking {
        val data = collector(listOf(character(1, 3, 4), character(2, 16)),
            snapshot(CharacterStat(1, 100, listOf(weapon(3))))).collect()
        assertEquals(3, data.players.size)
        assertEquals(setOf("/resources/images/weapon/3.png", "/resources/images/weapon/4.png", "/resources/images/weapon/16.png"),
            data.players.map { it.weaponImgUrl }.toSet())
        assertTrue(data.players.any { it.characterImgUrl == "/resources/images/character/2/CharProfile/1002.png" })
        val pending = data.players.filter { it.winRate == "暂无数据" }
        assertEquals(2, pending.size)
        assertTrue(pending.all { it.tier.isBlank() })
        assertEquals("25.00%", data.players.first().winRate)
        assertEquals(10_000, data.players.first().averageDamage)
    }

    @Test fun `no weapon statistics does not hide the playable roster`() = runBlocking {
        val data = collector(listOf(character(1, 3, 4), character(2, 16)), snapshot()).collect()
        assertEquals(3, data.players.size)
        assertTrue(data.players.all { it.winRate == "暂无数据" && it.tier.isBlank() })
    }

    @Test fun `zero game placeholder is not presented as D with fabricated win rate`() = runBlocking {
        val data = collector(listOf(character(1, 3)), snapshot(CharacterStat(1, 0,
            listOf(weapon(3, grade = "D", games = 0, score = null))))).collect()
        assertEquals("暂无数据", data.players.single().winRate)
        assertTrue(data.players.single().tier.isBlank())
    }

    @Test fun `populated statistics retain every grade and sorted rank order`() = runBlocking {
        val grades = listOf("D", "C", "B", "A", "S")
        val data = collector(listOf(character(1, 1, 2, 3, 4, 5)), snapshot(CharacterStat(1, 500,
            grades.mapIndexed { index, grade -> weapon(index + 1, grade, index.toDouble()) }))).collect()
        assertEquals(listOf("S", "A", "B", "C", "D"), data.players.map { it.tier })
        assertEquals(5, data.players.size)
    }

    @Test fun `duplicate metadata weapons are not repeated and observed alternate weapons are retained`() = runBlocking {
        val data = collector(listOf(character(1, 3, 3)), snapshot(CharacterStat(1, 100, listOf(weapon(4))))).collect()
        assertEquals(2, data.players.size)
        assertEquals("/resources/images/weapon/4.png", data.players.first().weaponImgUrl)
        assertEquals("暂无数据", data.players.last().winRate)
    }

    @Test fun `rank reaches the provider unchanged and missing response stays a friendly error`() = runBlocking {
        var received: DakGGRank? = null
        val collector = CharacterStatsCollector(loadCharacters = { DakGGCharactersResponse() }, loadStatistics = {
            received = it
            DakGGCharacterStatsResponse()
        })
        val error = assertFailsWith<MessageReplyException> { collector.collect(DakGGRank.IN_1000) }
        assertEquals(DakGGRank.IN_1000, received)
        assertEquals("数据统计中..", error.message)
    }

    @Test fun `statistics image does not reuse the twelve hour v2 snapshot`() {
        assertEquals(Duration.ofMinutes(15), CHARACTER_STATS_CACHE_MAX_AGE)
        assertEquals("character_stats_v3_diamond_plus.png", characterStatsImageFileName(DakGGRank.DIAMOND_PLUS))
        assertNotEquals(characterStatsImageFileName(DakGGRank.DIAMOND_PLUS), characterStatsImageFileName(DakGGRank.IN_1000))
    }

    @Test fun `new image request revalidates upstream statistics instead of using a stale HTTP snapshot`() {
        val request = freshCharacterStatisticsRequest(DakGGRank.IN_1000)
        assertEquals("no-cache", request.headers["Cache-Control"])
        assertTrue(request.url.contains("tier=in1000"))
        assertTrue(request.url.contains("matchingMode=RANK&teamMode=SQUAD"))
    }
}
