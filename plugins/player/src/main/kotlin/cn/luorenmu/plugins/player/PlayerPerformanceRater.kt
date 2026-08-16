package cn.luorenmu.plugins.player

import cn.luorenmu.request.api.entity.response.dakgg.DakGGCharacterStatsResponse
import cn.luorenmu.request.api.entity.response.game.BattleUserGamesResponse.UserGame
import cn.luorenmu.request.entity.module.MatchingMode

/**
 * Compares recent matches with DAK.GG's public baseline for the same character.
 */
internal object PlayerPerformanceRater {
    private const val DAMAGE_WEIGHT = 0.55
    private const val TAKEDOWN_WEIGHT = 0.25
    private const val PLACEMENT_WEIGHT = 0.20
    private const val ESCAPE_EFFECTIVE_RANK = 3.0
    private const val NORMAL_RATING_MIN_LEVEL = 50
    private const val LARGE_MMR_DROP_THRESHOLD = 200

    internal enum class Tier {
        BEGINNER,
        MMR_DROP,
        EXCEPTIONAL,
        EXCELLENT,
        GOOD,
        AVERAGE,
        BELOW_AVERAGE,
        POOR,
        INSUFFICIENT,
    }

    internal data class Rating(
        val tier: Tier,
        val score: Double?,
        val message: String,
    )

    internal data class Baseline(
        val averageDamage: Double,
        val averageTakedowns: Double,
        val averageRank: Double?,
    )

    internal data class GamePerformance(
        val gameId: Long,
        val matchingMode: Int,
        val characterNum: Long,
        val damageToPlayer: Long,
        val playerKill: Int,
        val playerAssistant: Int,
        val rank: Int,
    )

    private data class MatchSample(
        val game: GamePerformance,
        val baseline: Baseline,
    )

    fun rate(
        games: List<UserGame>,
        characterStats: DakGGCharacterStatsResponse?,
        matchingMode: MatchingMode,
        playerLevel: Int,
        recentMmr: List<Int>,
    ): String = evaluate(games, characterStats, matchingMode, playerLevel, recentMmr).message

    internal fun evaluate(
        games: List<UserGame>,
        characterStats: DakGGCharacterStatsResponse?,
        matchingMode: MatchingMode,
        playerLevel: Int,
        recentMmr: List<Int>,
    ): Rating = evaluateSamples(
        games = games.map { game ->
            GamePerformance(
                gameId = game.gameId,
                matchingMode = game.matchingMode,
                characterNum = game.characterNum,
                damageToPlayer = game.damageToPlayer,
                playerKill = game.playerKill,
                playerAssistant = game.playerAssistant,
                rank = game.gameRank,
            )
        },
        characterBaselines = buildBaselines(characterStats),
        matchingMode = matchingMode,
        playerLevel = playerLevel,
        recentMmr = recentMmr,
    )

    internal fun evaluateSamples(
        games: List<GamePerformance>,
        characterBaselines: Map<Long, Baseline>,
        matchingMode: MatchingMode,
        fallbackBaseline: Baseline? = null,
        playerLevel: Int = NORMAL_RATING_MIN_LEVEL,
        recentMmr: List<Int> = emptyList(),
    ): Rating {
        val relevantGames = games.filter { game ->
            matchingMode == MatchingMode.All || game.matchingMode == matchingMode.value
        }
        if (playerLevel < NORMAL_RATING_MIN_LEVEL) {
            return rating(Tier.BEGINNER, null, relevantGames)
        }
        val currentMmr = recentMmr.lastOrNull()
        val peakMmr = recentMmr.maxOrNull()
        if (currentMmr != null && peakMmr != null && peakMmr - currentMmr > LARGE_MMR_DROP_THRESHOLD) {
            return rating(Tier.MMR_DROP, null, relevantGames)
        }
        val samples = relevantGames.mapNotNull { game ->
            val baseline = characterBaselines[game.characterNum] ?: fallbackBaseline ?: return@mapNotNull null
            MatchSample(game, baseline)
        }

        if (samples.isEmpty()) {
            return rating(Tier.INSUFFICIENT, null, relevantGames)
        }

        val components = buildList {
            val expectedDamage = samples.sumOf { it.baseline.averageDamage }
            weightedRatio(samples.sumOf { it.game.damageToPlayer }.toDouble(), expectedDamage, DAMAGE_WEIGHT)
                ?.let(::add)

            val expectedTakedowns = samples.sumOf { it.baseline.averageTakedowns }
            val actualTakedowns = samples.sumOf { it.game.playerKill + it.game.playerAssistant }.toDouble()
            weightedRatio(actualTakedowns, expectedTakedowns, TAKEDOWN_WEIGHT)?.let(::add)

            val placementSamples = samples.mapNotNull { sample ->
                val expectedRank = sample.baseline.averageRank ?: return@mapNotNull null
                val actualRank = when (sample.game.rank) {
                    99 -> ESCAPE_EFFECTIVE_RANK
                    in 1..8 -> sample.game.rank.toDouble()
                    else -> return@mapNotNull null
                }
                expectedRank / actualRank
            }
            if (placementSamples.isNotEmpty()) {
                add(placementSamples.average().coerceIn(0.35, 1.80) to PLACEMENT_WEIGHT)
            }
        }

        if (components.isEmpty()) {
            return rating(Tier.INSUFFICIENT, null, relevantGames)
        }

        val score = components.sumOf { (value, weight) -> value * weight } / components.sumOf { it.second }
        val tier = when {
            score >= 1.45 -> Tier.EXCEPTIONAL
            score >= 1.20 -> Tier.EXCELLENT
            score >= 1.05 -> Tier.GOOD
            score >= 0.85 -> Tier.AVERAGE
            score >= 0.65 -> Tier.BELOW_AVERAGE
            else -> Tier.POOR
        }
        return rating(tier, score, relevantGames)
    }

    internal fun buildBaselines(characterStats: DakGGCharacterStatsResponse?): Map<Long, Baseline> =
        characterStats?.characterStatSnapshot?.characterStats.orEmpty().mapNotNull { character ->
            val weapons = character.weaponStats.filter { it.count > 0 }
            val games = weapons.sumOf { it.count }.takeIf { it > 0 } ?: return@mapNotNull null
            val totalPlace = weapons.sumOf { it.place.toLong() }
            character.key.toLong() to Baseline(
                averageDamage = weapons.sumOf { it.damageToPlayer.toLong() }.toDouble() / games,
                averageTakedowns = weapons.sumOf {
                    (it.playerKill + it.playerAssistant).toLong()
                }.toDouble() / games,
                averageRank = totalPlace.takeIf { it > 0 }?.div(games.toDouble()),
            )
        }.toMap()

    private fun weightedRatio(actual: Double, expected: Double, weight: Double): Pair<Double, Double>? {
        if (expected <= 0.0) return null
        return (actual / expected).coerceIn(0.35, 1.80) to weight
    }

    private fun rating(tier: Tier, score: Double?, games: List<GamePerformance>): Rating {
        val candidates = messages.getValue(tier)
        val stableKey = games.fold(17L) { hash, game -> hash * 31 + game.gameId }
        val message = candidates[Math.floorMod(stableKey, candidates.size.toLong()).toInt()]
        return Rating(tier, score, message)
    }

    internal val messages = mapOf(
        Tier.BEGINNER to listOf(
            "才刚踏上卢米亚岛就很棒喵！",
            "新手也很了不起喵！",
            "每一场都在积攒经验喵！",
            "你正在稳稳变强喵~",
            "明天会更厉害喵！",
            "你的高光还在后面喵！",
        ),
        Tier.MMR_DROP to listOf(
            "别担心，你只是回到了属于你自己的段位。",
            "在这个段位是不是越来越轻松了呢?"
        ),
        Tier.EXCEPTIONAL to listOf(
            "嗯~~~非常厉害喵！",
            "这战绩也太闪耀了喵！",
            "实验体都被你玩明白了喵！",
            "统治力拉满喵！",
            "数据全都在线喵！",
            "这是降维打击喵！",
        ),
        Tier.EXCELLENT to listOf(
            "打得很漂亮喵！",
            "这波状态火热喵！",
            "战斗效率很高喵！",
            "近期手感正盛喵！",
        ),
        Tier.GOOD to listOf(
            "状态在线喵！",
            "好！非常好喵！",
        ),
        Tier.AVERAGE to listOf(
            "一般般啦喵。",
            "表现合格喵。",
            "手感还可以再热一点喵。",
            "属于正常发挥喵！",
        ),
        Tier.BELOW_AVERAGE to listOf(
            "这几场有点可惜喵。",
            "状态稍冷喵。",
            "较为差劲喵。",
            "输出有些吃力喵。",
            "这轮手感不太顺喵！",
            "发挥低于平时水准喵。",
        ),
        Tier.POOR to listOf(
            "切，这可不太行喵！",
            "警报喵！正在呼叫群友支援。",
            "今天可能不宜排位喵",
            "伤害和名次都在迷路喵",
            "这状态有点危险喵",
            "惨兮兮喵……！",
        ),
        Tier.INSUFFICIENT to listOf(
            "想你了喵~",
            "今晚吃什么呢？",
            "今天也要开心喵~",
            "先喝口水呀？",
            "发会儿呆喵~",
            "等你好消息喵~",
        ),
    )
}
