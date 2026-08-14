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
import cn.luorenmu.service.ResourcesDownloadService
import kotlinx.coroutines.coroutineScope

/**
 * @author LoMu
 * Date 2026/5/24
 */
open class CharacterStatsCollector(
    private val resourcesDownloadService: ResourcesDownloadService = ResourcesDownloadService(),
) {

    suspend fun collect(
        teamMode: DakGGTeamMode = DakGGTeamMode.Squad,
        matchingMode: MatchingMode = MatchingMode.Rank,
        rank: DakGGRank = DakGGRank.DIAMOND_PLUS,
        tierOrCharacter: String = "s"
    ): CharacterStats {
        lateinit var characters: DakGGCharactersResponse
        lateinit var stats: DakGGCharacterStatsResponse

        coroutineScope {
            val charactersDF = ioAsync { EternalReturnDakGGApi.Data.GetCharacters.execute() }
            val statsDF = ioAsync {
                EternalReturnDakGGApi.Statistics.GetCharacterStats(teamMode, matchingMode, rank).execute()
            }
            characters = charactersDF.await()
            stats = statsDF.await()
        }

        val snapshot = stats.requireSnapshot()
        // 当 tierOrCharacter 为角色id时 按角色筛选 否则按评级筛选
        val filterCharacterId = tierOrCharacter.toLongOrNull()
        val flat = snapshot.characterStats
            .flatMap { charStat ->
                val characterId = charStat.key.toLong()
                val character = characters.getCharacterById(characterId)
                charStat.weaponStats.map { wp -> Triple(character, wp, if (wp.count > 0) wp.win.toDouble() / wp.count else 0.0) }
            }
            .sortedByDescending { (_, wp) -> wp.tierScore }
            .filter { (character, wp) ->
                if (filterCharacterId != null) {
                    character.id == filterCharacterId
                } else {
                    wp.tier.equals(tierOrCharacter, ignoreCase = true)
                }
            }

        if (flat.isEmpty()){
            throw MessageReplyException("数据统计中..")
        }
        val maxWinRate = flat.maxOfOrNull { (_, _, raw) -> raw } ?: 1.0

        val players = flat.mapIndexed { index, (character, wp, rawWin) ->
            val skinId = character.skins.firstOrNull()?.id ?: 0
            val relative = if (maxWinRate > 0) rawWin / maxWinRate * 100 else 0.0
            CharacterStats.CharacterStatsPlayer(
                rank = index + 1,
                characterImgUrl = ImageResourcesType.Character.getCharacterPath(
                    character.id.toInt(), skinId, DakGGCharacterImgType.CharProfile
                ),
                weaponImgUrl = ImageResourcesType.Weapon.getGeneralPath(wp.key.toString()),
                characterName = character.name,
                tier = wp.tier,
                rp = if (wp.count > 0 && wp.mmrGain > 0) String.format("%.1f", wp.mmrGain / wp.count.toDouble()) else "0",
                playCount = wp.count,
                winRate = if (wp.count > 0) "${String.format("%.1f", rawWin * 100)}%" else "0%",
                avgKill = if (wp.count > 0) String.format("%.1f", wp.playerKill.toDouble() / wp.count) else "0",
                top3Rate = if (wp.count > 0) "${String.format("%.1f", wp.top3.toDouble() / wp.count * 100)}%" else "0%",
                pick = if (wp.count > 0) String.format("%.1f", snapshot.tierGameCount.toDouble() / wp.count) else "0%",
                relativeWinRate = String.format("%.1f", relative),
                avgDmg = if (wp.count > 0)"${wp.damageToPlayer / wp.count}" else "0",
                avgRank =  if (wp.count > 0) "${String.format("%.1f", wp.place.toDouble() / wp.count)}%" else "0%",
            )
        }

        resourcesDownloadService.downloadCharacterTierIcons(players.map { it.tier })

        return CharacterStats(
            totalGames = snapshot.tierGameCount,
            totalPlayers = snapshot.tierCount,
            tierName = snapshot.tier,
            players = players,
            tier = rank.shortName
        )
    }
}

internal fun DakGGCharacterStatsResponse.requireSnapshot(): DakGGCharacterStatsResponse.CharacterStatSnapshot =
    characterStatSnapshot ?: throw MessageReplyException("数据统计中..")
