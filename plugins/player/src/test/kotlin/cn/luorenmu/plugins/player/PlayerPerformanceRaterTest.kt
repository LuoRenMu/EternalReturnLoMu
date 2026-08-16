package cn.luorenmu.plugins.player

import cn.luorenmu.request.api.entity.response.dakgg.DakGGCharacterStatsResponse
import cn.luorenmu.request.entity.module.MatchingMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 *
 * @author LoMu
 * Date 2026/8/16 14:11
 */
class PlayerPerformanceRaterTest {
    @Test
    fun `builds every-character baselines from public statistics`() {
        val response = DakGGCharacterStatsResponse(
            characterStatSnapshot = DakGGCharacterStatsResponse.CharacterStatSnapshot(
                dt = 7,
                patch = 12_010,
                matchingMode = 3,
                teamMode = 3,
                tier = "diamond_plus",
                tierCount = 1_000,
                tierGameCount = 1_000,
                characterStats = listOf(
                    DakGGCharacterStatsResponse.CharacterStat(
                        key = 1,
                        count = 3,
                        weaponStats = listOf(
                            publicWeapon(count = 2, damage = 10_000, kills = 4, assists = 2, place = 6),
                            publicWeapon(count = 1, damage = 8_000, kills = 2, assists = 1, place = 5),
                        ),
                    )
                ),
            )
        )

        val baseline = assertNotNull(PlayerPerformanceRater.buildBaselines(response)[1L])

        assertEquals(6_000.0, baseline.averageDamage)
        assertEquals(3.0, baseline.averageTakedowns)
        assertEquals(11.0 / 3.0, baseline.averageRank)
        assertTrue(PlayerPerformanceRater.buildBaselines(DakGGCharacterStatsResponse()).isEmpty())
    }

    @Test
    fun `players below level fifty always receive beginner encouragement`() {
        val strongGame = game(gameId = 1, damage = 12_000, kills = 12, assists = 12, rank = 1)

        val beginner = PlayerPerformanceRater.evaluateSamples(
            games = listOf(strongGame),
            characterBaselines = mapOf(1L to baseline()),
            matchingMode = MatchingMode.Rank,
            playerLevel = 49,
            recentMmr = listOf(2_100, 1_800),
        )
        val levelFifty = PlayerPerformanceRater.evaluateSamples(
            games = listOf(strongGame),
            characterBaselines = mapOf(1L to baseline()),
            matchingMode = MatchingMode.Rank,
            playerLevel = 50,
        )

        assertEquals(PlayerPerformanceRater.Tier.BEGINNER, beginner.tier)
        assertTrue(beginner.message in PlayerPerformanceRater.messages.getValue(PlayerPerformanceRater.Tier.BEGINNER))
        assertTrue(levelFifty.tier != PlayerPerformanceRater.Tier.BEGINNER)
    }

    @Test
    fun `drawdown over two hundred points from recent peak receives consolation`() {
        val game = game(gameId = 1)

        val dropped = PlayerPerformanceRater.evaluateSamples(
            games = listOf(game),
            characterBaselines = mapOf(1L to baseline()),
            matchingMode = MatchingMode.Rank,
            recentMmr = listOf(1_800, 2_100, 1_899),
        )
        val boundary = PlayerPerformanceRater.evaluateSamples(
            games = listOf(game),
            characterBaselines = mapOf(1L to baseline()),
            matchingMode = MatchingMode.Rank,
            recentMmr = listOf(2_100, 1_900),
        )

        assertEquals(PlayerPerformanceRater.Tier.MMR_DROP, dropped.tier)
        assertEquals("别担心，你只是回到了属于你自己的段位。", dropped.message)
        assertTrue(boundary.tier != PlayerPerformanceRater.Tier.MMR_DROP)
    }

    @Test
    fun `higher damage and takedowns produce a better rating`() {
        val baseline = baseline()
        val strong = PlayerPerformanceRater.evaluateSamples(
            games = listOf(game(gameId = 1, damage = 12_000, kills = 12, assists = 12, rank = 1)),
            characterBaselines = mapOf(1L to baseline),
            matchingMode = MatchingMode.Rank,
        )
        val weak = PlayerPerformanceRater.evaluateSamples(
            games = listOf(game(gameId = 2, damage = 3_000, kills = 3, assists = 3, rank = 8)),
            characterBaselines = mapOf(1L to baseline),
            matchingMode = MatchingMode.Rank,
        )

        assertTrue(assertNotNull(strong.score) > assertNotNull(weak.score))
        assertEquals(PlayerPerformanceRater.Tier.EXCEPTIONAL, strong.tier)
        assertEquals(PlayerPerformanceRater.Tier.POOR, weak.tier)
    }

    @Test
    fun `each match uses its own public character baseline`() {
        val lowDamageBaseline = baseline(damage = 6_000)
        val highDamageBaseline = baseline(damage = 12_000)
        val stats = mapOf(1L to lowDamageBaseline, 2L to highDamageBaseline)
        val againstLowBaseline = PlayerPerformanceRater.evaluateSamples(
            listOf(game(gameId = 1, characterNum = 1, damage = 9_000)), stats, MatchingMode.Rank,
        )
        val againstHighBaseline = PlayerPerformanceRater.evaluateSamples(
            listOf(game(gameId = 2, characterNum = 2, damage = 9_000)), stats, MatchingMode.Rank,
        )

        assertTrue(assertNotNull(againstLowBaseline.score) > assertNotNull(againstHighBaseline.score))
    }

    @Test
    fun `requested matching mode excludes unrelated games`() {
        val stats = mapOf(1L to baseline())
        val rank = game(gameId = 1, matchingMode = MatchingMode.Rank.value, damage = 10_000, rank = 1)
        val normal = game(gameId = 2, matchingMode = MatchingMode.Normal.value, damage = 1_000, rank = 8)

        val rankRating = PlayerPerformanceRater.evaluateSamples(listOf(rank, normal), stats, MatchingMode.Rank)
        val normalRating = PlayerPerformanceRater.evaluateSamples(listOf(rank, normal), stats, MatchingMode.Normal)

        assertTrue(assertNotNull(rankRating.score) > assertNotNull(normalRating.score))
    }

    @Test
    fun `missing public character data returns casual small talk`() {
        val rating = PlayerPerformanceRater.evaluateSamples(
            games = listOf(game(gameId = 1)),
            characterBaselines = emptyMap(),
            matchingMode = MatchingMode.Rank,
        )

        assertEquals(PlayerPerformanceRater.Tier.INSUFFICIENT, rating.tier)
        assertTrue(rating.message in PlayerPerformanceRater.messages.getValue(PlayerPerformanceRater.Tier.INSUFFICIENT))
        assertTrue("想你了喵~" in PlayerPerformanceRater.messages.getValue(PlayerPerformanceRater.Tier.INSUFFICIENT))
        assertTrue("今晚吃什么呢？" in PlayerPerformanceRater.messages.getValue(PlayerPerformanceRater.Tier.INSUFFICIENT))
    }

    @Test
    fun `rating copy contains more than twenty stable alternatives`() {
        assertTrue(PlayerPerformanceRater.messages.values.flatten().size > 20)
        assertTrue(PlayerPerformanceRater.messages.values.all { it.isNotEmpty() })
    }

    private fun baseline(
        damage: Int = 6_000,
    ) = PlayerPerformanceRater.Baseline(
        averageDamage = damage.toDouble(),
        averageTakedowns = 12.0,
        averageRank = 4.0,
    )

    private fun game(
        gameId: Long,
        characterNum: Long = 1,
        matchingMode: Int = MatchingMode.Rank.value,
        damage: Long = 6_000,
        kills: Int = 6,
        assists: Int = 6,
        rank: Int = 4,
    ) = PlayerPerformanceRater.GamePerformance(
        gameId = gameId,
        matchingMode = matchingMode,
        characterNum = characterNum,
        rank = rank,
        playerKill = kills,
        playerAssistant = assists,
        damageToPlayer = damage,
    )

    private fun publicWeapon(
        count: Int,
        damage: Int,
        kills: Int,
        assists: Int,
        place: Int,
    ) = DakGGCharacterStatsResponse.WeaponStat(
        key = 1,
        count = count,
        win = 0,
        top3 = 0,
        place = place,
        playerKill = kills,
        playerAssistant = assists,
        playerDeaths = 0,
        damageToPlayer = damage,
        damageToMonster = 0,
        monsterKill = 0,
        teamKill = 0,
        mmrGain = 0,
        tier = "A",
        viewContribution = 0,
    )
}
