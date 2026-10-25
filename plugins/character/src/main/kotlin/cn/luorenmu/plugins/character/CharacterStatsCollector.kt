package cn.luorenmu.plugins.character

import cn.luorenmu.exception.MessageReplyException
import cn.luorenmu.request.api.Api.Companion.ioAsync
import cn.luorenmu.request.api.entity.module.ImageResourcesType
import cn.luorenmu.request.api.entity.response.dakgg.DakGGCharacterImgType
import cn.luorenmu.request.api.entity.response.dakgg.DakGGCharacterStatsResponse
import cn.luorenmu.request.api.entity.response.dakgg.DakGGCharactersResponse
import cn.luorenmu.request.api.impl.EternalReturnDakGGApi
import cn.luorenmu.request.entity.module.DakGGTeamMode
import cn.luorenmu.request.entity.module.DakGGRank
import cn.luorenmu.request.entity.module.MatchingMode
import cn.luorenmu.service.entity.CharacterStats
import io.ktor.client.call.body
import kotlinx.coroutines.coroutineScope
import java.util.Locale
import kotlin.math.roundToInt

/**
 * @author LoMu
 * Date 2026/5/24
 */
open class CharacterStatsCollector(
    private val loadCharacters: suspend () -> DakGGCharactersResponse = {
        EternalReturnDakGGApi.Data.GetCharacters.refresh().body()
    },
    private val loadStatistics: suspend (DakGGRank) -> DakGGCharacterStatsResponse = { rank ->
        freshCharacterStatisticsRequest(rank).refresh().body()
    },
) {

    suspend fun collect(
        rank: DakGGRank = DakGGRank.DIAMOND_PLUS,
    ): CharacterStats {
        lateinit var characters: DakGGCharactersResponse
        lateinit var stats: DakGGCharacterStatsResponse

        coroutineScope {
            val charactersDF = ioAsync { loadCharacters() }
            val statsDF = ioAsync { loadStatistics(rank) }
            characters = charactersDF.await()
            stats = statsDF.await()
        }

        val snapshot = stats.requireSnapshot()
        val statsByCharacter = snapshot.characterStats.associate { character ->
            character.key.toLong() to character.weaponStats.associateBy { it.key.toLong() }
        }
        val characterIds = (characters.characters.map { it.id } + statsByCharacter.keys).distinct()
        val flat = characterIds.flatMap { characterId ->
            val character = characters.getCharacterById(characterId)
            val weapons = statsByCharacter[characterId].orEmpty()
            // Some playable weapons have no snapshot row yet; they still belong in the roster.
            (character.weaponTypes.map { it.id } + weapons.keys).distinct().filter { it > 0 }.map { weaponId ->
                CharacterWeapon(character, weaponId, weapons[weaponId]?.takeIf { it.count > 0 })
            }
        }.sortedWith(compareByDescending<CharacterWeapon> { it.stat != null }
            .thenByDescending { it.stat?.tierScore }
            .thenByDescending { it.stat?.count })

        if (flat.isEmpty()) {
            throw MessageReplyException("数据统计中..")
        }

        val players = flat.map { (character, weaponId, weapon) ->
            val skinId = character.skins.firstOrNull()?.id ?: 0
            CharacterStats.CharacterStatsPlayer(
                characterImgUrl = ImageResourcesType.Character.getCharacterPath(
                    character.id.toInt(), skinId, DakGGCharacterImgType.CharProfile
                ),
                weaponImgUrl = ImageResourcesType.Weapon.getGeneralPath(weaponId.toString()),
                tier = weapon?.tier.orEmpty(),
                rank = weapon?.rank?.count ?: 0,
                rankSize = weapon?.rank?.size ?: 0,
                winRate = weapon?.let { winRate(it.win, it.count) } ?: NO_CHARACTER_STATISTICS,
                averageDamage = weapon?.let { averageDamage(it.damageToPlayer, it.count) } ?: 0,
            )
        }

        return CharacterStats(
            players = players,
            tier = rank.shortName,
        )
    }

    private data class CharacterWeapon(
        val character: DakGGCharactersResponse.DakGGCharacterById,
        val weaponId: Long,
        val stat: DakGGCharacterStatsResponse.WeaponStat?,
    )
}

internal const val NO_CHARACTER_STATISTICS = "暂无数据"

internal fun freshCharacterStatisticsRequest(rank: DakGGRank) =
    EternalReturnDakGGApi.Statistics.GetCharacterStats(DakGGTeamMode.Squad, MatchingMode.Rank, rank).apply {
        headers["Cache-Control"] = "no-cache"
    }

internal fun winRate(wins: Int, games: Int): String {
    if (wins <= 0 || games <= 0) return "0.0%"
    return "${String.format(Locale.ROOT, "%.2f", wins.toDouble() / games * 100)}%"
}

internal fun averageDamage(totalDamage: Int, games: Int): Int =
    if (totalDamage > 0 && games > 0) (totalDamage.toDouble() / games).roundToInt() else 0

internal fun DakGGCharacterStatsResponse.requireSnapshot(): DakGGCharacterStatsResponse.CharacterStatSnapshot =
    characterStatSnapshot ?: throw MessageReplyException("数据统计中..")
