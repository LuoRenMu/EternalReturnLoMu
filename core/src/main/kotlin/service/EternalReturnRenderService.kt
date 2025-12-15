package cn.luorenmu.service

import cn.luorenmu.exception.MessageReplyException
import cn.luorenmu.request.api.EternalReturnDakGGApiClient
import cn.luorenmu.request.api.EternalReturnOpenApiClient
import cn.luorenmu.request.api.entity.module.ImageResourcesType
import cn.luorenmu.request.api.entity.response.dakgg.DakGGCharacterImgType
import cn.luorenmu.request.api.entity.response.dakgg.DakGGCharactersResponse
import cn.luorenmu.request.api.entity.response.dakgg.DakGGLeaderboardResponse
import cn.luorenmu.request.api.entity.response.dakgg.DakGGTiersResponse
import cn.luorenmu.request.api.entity.response.game.BattleUserGamesResponse.UserGame
import cn.luorenmu.request.api.entity.response.user.UserStatsResponse
import cn.luorenmu.request.entity.module.DakGGServerName
import cn.luorenmu.request.entity.module.DakGGTeamMode
import cn.luorenmu.request.entity.module.MatchingMode
import cn.luorenmu.service.entity.EternalReturnEquip
import cn.luorenmu.service.entity.EternalReturnOldName
import cn.luorenmu.service.entity.EternalReturnPlayRender
import cn.luorenmu.service.entity.TierStatistics
import kotlinx.coroutines.*
import love.forte.simbot.message.toText
import org.koin.java.KoinJavaComponent.inject
import java.util.concurrent.CopyOnWriteArrayList
import java.util.stream.Collectors

/**
 *
 * @author LoMu
 * Date 2025/11/21 14:20
 */
class EternalReturnRenderService {
    private val executors: ExecutorCoroutineDispatcher by inject(
        ExecutorCoroutineDispatcher::class.java
    )

    private fun <T> CoroutineScope.ioAsync(block: suspend CoroutineScope.() -> T) =
        async(executors, block = block)


    data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    suspend fun getEternalReturnRender(
        userId: String,
        nickname: String,
        matchingMode: MatchingMode,
    ): EternalReturnPlayRender {
        /**
         * 数据收集
         */
        val userStats = EternalReturnOpenApiClient.getUserStats(userId, 35, matchingMode)
        val (profile, characters, tiers, season) = coroutineScope {
            val profileDF = ioAsync { EternalReturnDakGGApiClient.getProfile(nickname) }
            val charactersDF = ioAsync { EternalReturnDakGGApiClient.getCharacters() }
            val tierDF = ioAsync { EternalReturnDakGGApiClient.getTiers() }
            val seasonDF = ioAsync { EternalReturnDakGGApiClient.getDataCurrentSeason() }
            Quad(profileDF.await(), charactersDF.await(), tierDF.await(), seasonDF.await())
        }


        val gamesResponse = EternalReturnOpenApiClient.getGamesByUserNum(userId)
        val playerSeasonOverview = profile.playerSeasonOverviews.firstOrNull()

        val accountLevel = profile.player.accountLevel
        val profileImageUrl = if (playerSeasonOverview != null) {
            val characterState = playerSeasonOverview.characterStats.first()
            val skinState = characterState.skinStats!!.first()
            ImageResourcesType.getCharacterPath(
                characterState.key.toInt(),
                skinState.key,
                DakGGCharacterImgType.CharResult
            )
        } else ""

        var tier: DakGGTiersResponse.EternalReturnTier = tiers.getUnRank()
        val latestPlaySeason = profile.playerSeasons.firstOrNull() ?: run {
            throw MessageReplyException("该玩家无任何游玩数据".toText())
        }


        /**
         * 当前赛季有玩过排位
         */
        if (latestPlaySeason.mmr != 0) {
            tier = tiers.getTierById(latestPlaySeason.tierId)
        }

        /**
         * 左边栏游玩数据显示
         */
        val eternalReturnPlayerData = EternalReturnPlayRender.EternalReturnPlayerData()
        eternalReturnPlayerData.tierImageUrl = ImageResourcesType.TierRound.getGeneralPath(tier.id.toString())
        eternalReturnPlayerData.rpName = tier.name

        /**
         * 排位数据
         */
        if (userStats.userStats.isNotEmpty()) {
            val first = userStats.userStats.first()
            eternalReturnPlayerData.rp = first.mmr.toString()
            eternalReturnPlayerData.tierImageUrl = ImageResourcesType.TierRound.getGeneralPath(tier.id.toString())
            eternalReturnPlayerData.play = first.totalGames
            eternalReturnPlayerData.avgTk = (first.totalTeamKills / first.totalGames).toString()
            eternalReturnPlayerData.avgKill = first.averageKills.toString()
            eternalReturnPlayerData.avgRank = String.format("%.2f", first.rankPercent)
            eternalReturnPlayerData.avgDmg = String.format("%.2f", first.averageHunts)
            eternalReturnPlayerData.avgAssists = String.format("%.2f", first.averageAssistants)
            eternalReturnPlayerData.top1 = first.top1.toString()
            eternalReturnPlayerData.top2 = first.top2.toString()
            eternalReturnPlayerData.top3 = first.top3.toString()
        }


        /**
         * 分数波动
         */
        var playerMMRStats: EternalReturnPlayRender.EternalReturnPlayerMMRStats? = null
        profile.playerSeasonOverviews.firstOrNull()?.let { overview ->
            if (overview.mmrStats.isNotEmpty()) {
                val mmrStats = overview.mmrStats.take(7).reversed()
                playerMMRStats =
                    EternalReturnPlayRender.EternalReturnPlayerMMRStats(mmrDate = mmrStats.map { mmrs ->
                        val dateStr = mmrs.first().toString().substring(4)
                        dateStr.substring(0, 2) + "/" + dateStr.substring(2)
                    }, mmr = mmrStats.map { mmrs -> mmrs[1] })
            }
        }

        return EternalReturnPlayRender(
            mmrStats = playerMMRStats,
            nickName = nicknameHide(nickname),
            profileImageUrl = profileImageUrl,
            level = accountLevel,
            data = eternalReturnPlayerData,
            matches = gamesResponse.userGames.map { gameConvertMatcher(it, characters) },
            recentPlayers = mutableListOf(),
            characterUseStats = mutableListOf(),
            season = season.name,
        )
    }

    private fun gameConvertMatcher(
        game: UserGame,
        characters: DakGGCharactersResponse,
    ): EternalReturnPlayRender.EternalReturnPlayerMatchData {
        val killAndAssist = game.playerKill + game.playerAssistant
        return EternalReturnPlayRender.EternalReturnPlayerMatchData(
            rp = game.mmrAfter,
            rpChange = game.mmrGain,
            serverName = game.serverName,
            nickName = game.nickname,
            characterName = characters.getCharacterById(game.characterNum).name,
            rank = game.gameRank,
            type = MatchingMode.convert(game.matchingMode).modeName,
            kill = game.playerKill,
            tk = game.teamKill,
            equips = gameEquip(game),
            weaponUrl = ImageResourcesType.Weapon.getGeneralPath(game.bestWeapon.toString()),
            tacticalSkillUrl = ImageResourcesType.TacticalSkill.getGeneralPath(game.tacticalSkillGroup.toString()),
            traitSkillUrl = ImageResourcesType.TraitSkill.getGeneralPath(game.traitFirstCore.toString()),
            traitSkillGroupUrl = if (MatchingMode.convert(game.matchingMode) == MatchingMode.Cobalt)
                ImageResourcesType.TraitSkillGroupPlaceholder.getGeneralPath("")
            else ImageResourcesType.TraitSkillGroup.getGeneralPath(
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

    private fun gameEquip(game: UserGame): MutableList<EternalReturnEquip> {
        val equipList = mutableListOf<EternalReturnEquip>()
        for ((index, value) in game.equipment) {
            val equip = EternalReturnEquip(
                itemBgUrl = ImageResourcesType.ItemBg.getGeneralPath(game.equipmentGrade[index].toString()),
                itemUrl = ImageResourcesType.Item.getGeneralPath(value.toString())
            )
            equipList.add(equip)
        }
        return equipList
    }

    private fun nicknameHide(nickname: String): String {
        val length = nickname.length
        return if (length < 3) {
            nickname.replace(nickname.substring(1, length - 1), " * ")
        } else {
            nickname.replace(nickname.substring(1, length - 1), " * ".repeat(length - 2))
        }
    }

    suspend fun getCutoffsAndTierNumber(serverName: DakGGServerName): TierStatistics {
        val (leaderboard, td, season) = coroutineScope {
            val leaderboardDeferred = async(executors) {
                val type = EternalReturnDakGGApiClient.getDataCurrentSeason().type
                EternalReturnDakGGApiClient.getCutoffsAndLeaderboard(1, type, serverName, DakGGTeamMode.Squad)
            }
            val tierDistributionDeferred = async(executors) {
                EternalReturnDakGGApiClient.getTierDistributions(DakGGTeamMode.Squad)
            }
            val seasonDF = async(executors) { EternalReturnDakGGApiClient.getDataCurrentSeason() }
            Triple(leaderboardDeferred.await(), tierDistributionDeferred.await(), seasonDF.await())
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
        when (leaderboard.cutoffs.size) {
            1 -> {
                eternal = leaderboard.cutoffs[0]
                demigod = leaderboard.cutoffs[0]
            }

            2 -> {
                eternal = leaderboard.cutoffs[1]
                demigod = leaderboard.cutoffs[0]
            }

            else -> {
                throw MessageReplyException("数据收集中...".toText())
            }
        }

        val rateStr = rate.mapValues { String.format("%.2f", it.value * 100) }.mapKeys { it.key.toString() }
        val tierTypesStr = tierTypes.map { it.toString() }
        val countStr = count.mapKeys { it.key.toString() }
        return TierStatistics(
            season.name,
            tierTypesStr,
            countStr,
            rateStr,
            eternal,
            demigod
        )
    }

    /**
     * TODO 该接口需要限制访问. 权限验证/群验证/角色验证
     */
    suspend fun oldName(nickname: String): EternalReturnOldName {
        val (userResponse, dataCurrentSeason) = coroutineScope {
            val userResponseDF = ioAsync {
                EternalReturnOpenApiClient.getUserNumByUserNickName(nickname)
            }
            val dataCurrentSeasonDF = ioAsync {
                EternalReturnOpenApiClient.getDataCurrentSeason()
            }
            userResponseDF.await() to dataCurrentSeasonDF.await()
        }
        val userId = userResponse.user.userId
        val seasonID = dataCurrentSeason.seasonID
        val userStatsResponses = coroutineScope {
            val list = CopyOnWriteArrayList<UserStatsResponse>()
            for (i in 1..<seasonID) {
                launch(executors) {
                    // TODO 缓存来自底层 在没有缓存的情况下会同时发送大量请求
                    val resp = EternalReturnOpenApiClient.getUserStats(userId, i, MatchingMode.Rank)
                    list.add(resp)
                }
            }
            list
        }

        val oldNames = mutableSetOf<String>()
        for (response in userStatsResponses) {
            response.userStats.firstOrNull()?.nickname?.let { oldName ->
                if (!oldName.equals(nickname, true)) {
                    oldNames.add(oldName)
                }
            }
        }
        if (oldNames.isEmpty()) {
            throw MessageReplyException("没有找到该玩家之前的昵称".toText())
        }
        return EternalReturnOldName(
            nickname,
            oldNames.toList()
        )

    }
}