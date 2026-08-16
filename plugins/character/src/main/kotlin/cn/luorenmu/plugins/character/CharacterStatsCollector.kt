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
import kotlinx.coroutines.coroutineScope
import java.util.Locale

/**
 * @author LoMu
 * Date 2026/5/24
 */
open class CharacterStatsCollector {

    suspend fun collect(
        rank: DakGGRank = DakGGRank.DIAMOND_PLUS,
    ): CharacterStats {
        lateinit var characters: DakGGCharactersResponse
        lateinit var stats: DakGGCharacterStatsResponse

        coroutineScope {
            val charactersDF = ioAsync { EternalReturnDakGGApi.Data.GetCharacters.execute() }
            val statsDF = ioAsync {
                EternalReturnDakGGApi.Statistics.GetCharacterStats(
                    DakGGTeamMode.Squad,
                    MatchingMode.Rank,
                    rank,
                ).execute()
            }
            characters = charactersDF.await()
            stats = statsDF.await()
        }

        val snapshot = stats.requireSnapshot()
        val flat = snapshot.characterStats
            .flatMap { charStat ->
                val characterId = charStat.key.toLong()
                val character = characters.getCharacterById(characterId)
                charStat.weaponStats.map { weapon -> character to weapon }
            }
            .sortedByDescending { (_, weapon) -> weapon.tierScore }

        if (flat.isEmpty()){
            throw MessageReplyException("数据统计中..")
        }

        val players = flat.map { (character, weapon) ->
            val skinId = character.skins.firstOrNull()?.id ?: 0
            CharacterStats.CharacterStatsPlayer(
                characterImgUrl = ImageResourcesType.Character.getCharacterPath(
                    character.id.toInt(), skinId, DakGGCharacterImgType.CharProfile
                ),
                weaponImgUrl = ImageResourcesType.Weapon.getGeneralPath(weapon.key.toString()),
                tier = weapon.tier,
                pickRate = selectionRate(weapon.count, snapshot.tierGameCount),
                playCount = weapon.count,
            )
        }

        return CharacterStats(
            players = players,
            tier = rank.shortName,
        )
    }
}

internal fun selectionRate(playCount: Int, totalGames: Int): String {
    if (playCount <= 0 || totalGames <= 0) return "0.0%"
    return "${String.format(Locale.ROOT, "%.2f", playCount.toDouble() / totalGames * 100)}%"
}

internal fun DakGGCharacterStatsResponse.requireSnapshot(): DakGGCharacterStatsResponse.CharacterStatSnapshot =
    characterStatSnapshot ?: throw MessageReplyException("数据统计中..")
