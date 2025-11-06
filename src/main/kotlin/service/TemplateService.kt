package cn.luorenmu.service

import cn.luorenmu.exception.MessageReplyException
import cn.luorenmu.render.entity.EternalReturnRender
import cn.luorenmu.request.api.EternalReturnDakGGApiClient
import cn.luorenmu.request.api.EternalReturnOpenApiClient
import cn.luorenmu.request.api.entity.module.ImageResourcesType
import cn.luorenmu.request.api.entity.response.dakgg.DakGGCharacterImgType
import cn.luorenmu.request.api.entity.response.dakgg.DakGGLeaderboardResponse
import cn.luorenmu.request.api.entity.response.dakgg.getCharacterById
import cn.luorenmu.request.entity.module.DakGGServerName
import cn.luorenmu.request.entity.module.DakGGTeamMode
import cn.luorenmu.request.entity.module.MatchingMode
import cn.luorenmu.service.entity.TierStatistics
import io.ktor.server.application.*
import io.ktor.server.freemarker.*
import io.ktor.server.response.*
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import love.forte.simbot.message.toText
import java.util.concurrent.Executors
import java.util.stream.Collectors

/**
 *
 * @author LoMu
 * Date 2025/11/1 23:32
 */
class TemplateService {
    private val executors = Executors.newFixedThreadPool(10).asCoroutineDispatcher()
    suspend fun ApplicationCall.tierStatisticsNumber(serverName: DakGGServerName) {
        val cutoffsAndTierNumber = getCutoffsAndTierNumber(serverName)
        respond(
            FreeMarkerContent(
                "tier_statistics_number.ftl",
                cutoffsAndTierNumber
            )
        )
    }

    suspend fun ApplicationCall.searchPlayer(nickname: String) {
        val user = EternalReturnOpenApiClient.getUserNumByUserNickName(nickname)
        val eternalReturnRender = getEternalReturnRender(user.user.userNum, nickname, MatchingMode.Rank)
        respond(
            FreeMarkerContent(
                "search_player.ftl",
                eternalReturnRender
            )
        )
    }

    private suspend fun getEternalReturnRender(
        userNum: Long,
        nickname: String,
        matchingMode: MatchingMode,
    ): EternalReturnRender {
        val userStats = EternalReturnOpenApiClient.getUserStats(userNum, 35, matchingMode)
        val (profile, characters) = coroutineScope {
            val profileDF = async(executors) { EternalReturnDakGGApiClient.getProfile(nickname) }
            val charactersDF = async(executors) { EternalReturnDakGGApiClient.getCharacters() }
            profileDF.await() to charactersDF.await()
        }
        val gamesResponse = EternalReturnOpenApiClient.getGamesByUserNum(userNum)
        val playerSeasonOverview = profile.playerSeasonOverviews.firstOrNull()

        var accountLevel = 0
        val profileImageUrl = if (playerSeasonOverview != null) {
            val characterState = playerSeasonOverview.characterStats.first()
            val skinState = characterState.skinStats!!.first()
            ImageResourcesType.getCharacterPath(
                characterState.key.toInt(),
                skinState.key,
                DakGGCharacterImgType.CharResult
            )
        } else ""


        var latestPlaySeasonId = profile.playerSeasonOverviews.firstOrNull()?.seasonID
        if (gamesResponse.userGames.isNotEmpty()) {
            val firstGame = gamesResponse.userGames.first()
            accountLevel = firstGame.accountLevel
            latestPlaySeasonId?.let {
                /**
                 * 当前赛季没有玩过排位
                 */
                if (it != firstGame.seasonId) {
                    latestPlaySeasonId = firstGame.seasonId
                }
            }
        }
        val eternalReturnPlayerData = EternalReturnRender.EternalReturnPlayerData()

        /**
         * 该赛季排位数据
         */
        if (userStats.userStats.isNotEmpty()) {
            val first = userStats.userStats.first()
            eternalReturnPlayerData.rp = first.mmr.toString()
            eternalReturnPlayerData.rpName = "未知"
            eternalReturnPlayerData.play = first.totalGames
            eternalReturnPlayerData.avgTk = (first.totalTeamKills / first.totalGames).toString()
            eternalReturnPlayerData.avgKill = first.averageKills.toString()
            eternalReturnPlayerData.avgRank = first.rank.toString()
        } else {
            eternalReturnPlayerData.tierImageUrl = ImageResourcesType.TierRound.getGeneralPath("0")
            eternalReturnPlayerData
        }


        val mapList = gamesResponse.userGames.map { game ->
            val killAndAssist = game.playerKill + game.playerAssistant
            EternalReturnRender.EternalReturnPlayerMatchData(
                serverName = game.serverName,
                nickName = game.nickname,
                characterName = characters.getCharacterById(game.characterNum).name,
                rank = game.gameRank,
                type = MatchingMode.convert(game.matchingMode).modeName,
                kill = game.playerKill,
                tk = game.teamKill,
                weaponUrl = ImageResourcesType.Weapon.getGeneralPath(game.bestWeapon.toString()),
                tacticalSkillUrl = ImageResourcesType.TacticalSkill.getGeneralPath(game.tacticalSkillGroup.toString()),
                traitSkillUrl = ImageResourcesType.TraitSkill.getGeneralPath(game.traitFirstCore.toString()),
                traitSkillGroupUrl = ImageResourcesType.TraitSkillGroup.getGeneralPath(
                    game.traitSecondSub.first().toString()
                ),
                characterAvatarUrl = ImageResourcesType.getCharacterPath(
                    game.characterNum.toInt(),
                    game.skinCode,
                    DakGGCharacterImgType.CharProfile
                ),
                assist = game.playerAssistant,
                gameId = game.gameId.toString(),
                dmg = game.damageToPlayer,
                kda = if (game.playerDeaths == 0) killAndAssist.toDouble()
                else killAndAssist.toDouble() / game.playerDeaths,
                routeId = if (game.routeIdOfStart != 0L) game.routeIdOfStart.toString() else "Private",
                version = "${game.versionMajor}.${game.versionMinor}"
            )
        }

        return EternalReturnRender(
            userNum = userNum,
            nickName = nickname,
            profileImageUrl = profileImageUrl,
            level = accountLevel,
            data = eternalReturnPlayerData,
            matches = mapList.toMutableList(),
            recentPlayers = mutableListOf(),
            characterUseStats = mutableListOf(),
            season = "35",
        )
    }

    private suspend fun getCutoffsAndTierNumber(serverName: DakGGServerName): TierStatistics {
        val (leaderboard, td) = coroutineScope {
            val leaderboardDeferred = async(executors) {
                val type = EternalReturnDakGGApiClient.getDataCurrentSeason().type
                EternalReturnDakGGApiClient.getCutoffsAndLeaderboard(1, type, serverName, DakGGTeamMode.Squad)
            }
            val tierDistributionDeferred = async(executors) {
                EternalReturnDakGGApiClient.getTierDistributions(DakGGTeamMode.Squad)
            }
            leaderboardDeferred.await() to tierDistributionDeferred.await()
        }

        // 段位
        val tierTypes = td.distributions.stream().map { ds -> ds.tierType }.distinct().sorted { o1, o2 ->
            val i1 = if (o1 < 10) o1 * 10 else o1
            val i2 = if (o2 < 10) o2 * 10 else o2
            i1 - i2
        }.collect(Collectors.toList())

        val count = mutableMapOf<Int, Int>()
        val rate = mutableMapOf<Int, Double>()


        // 收集整个段位的人数和占率
        for (distribution in td.distributions) {
            count[distribution.tierType]?.let {
                count[distribution.tierType] = it + distribution.count
            } ?: run {
                count[distribution.tierType] = distribution.count
            }
            rate[distribution.tierType]?.let {
                rate[distribution.tierType] = it + distribution.rate
            } ?: run {
                rate[distribution.tierType] = distribution.rate
            }
        }

        // 预前赛或无永恒
        if (leaderboard.cutoffs.isEmpty()) {
            throw MessageReplyException("数据收集中...".toText())
        }
        val eternal: DakGGLeaderboardResponse.Cutoffs
        val demigod: DakGGLeaderboardResponse.Cutoffs
        if (leaderboard.cutoffs.size == 1) {
            eternal = leaderboard.cutoffs[0]
            demigod = leaderboard.cutoffs[0]
        } else {
            eternal = leaderboard.cutoffs[1]
            demigod = leaderboard.cutoffs[0]
        }

        val rateStr = rate.mapValues { String.format("%.2f", it.value * 100) }.mapKeys { it.key.toString() }
        val tierTypesStr = tierTypes.map { it.toString() }
        val countStr = count.mapKeys { it.key.toString() }
        return TierStatistics(
            tierTypesStr,
            countStr,
            rateStr,
            eternal,
            demigod
        )
    }
}