package cn.luorenmu.service

import cn.luorenmu.exception.MessageReplyException
import cn.luorenmu.request.api.Api.Companion.ioAsync
import cn.luorenmu.request.api.Api.Companion.ioLaunch
import cn.luorenmu.request.api.EternalReturnDakGGApi
import cn.luorenmu.request.api.EternalReturnOpenApi
import cn.luorenmu.request.api.entity.module.ImageResourcesType
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
import kotlinx.coroutines.coroutineScope
import love.forte.simbot.message.toText
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.CopyOnWriteArrayList
import java.util.stream.Collectors

/**
 *
 * @author LoMu
 * Date 2025/11/21 14:20
 */
class EternalReturnRenderService {


    data class Quint<A, B, C, D, E>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E)

    suspend fun getEternalReturnRender(
        nickname: String,
        matchingMode: MatchingMode,
    ): EternalReturnPlayRender {
        /**
         * 数据收集
         */
        // val user = EternalReturnOpenApiClient.getUser(userId, 35, matchingMode)

        val userId = EternalReturnOpenApi.User.GetIdByNickName(nickname).execute().user.userId
        val (profile, characters, tiers, season, gamesResponse) = coroutineScope {
            val profileDF = ioAsync { EternalReturnDakGGApi.User.GetProfile(nickname).execute() }
            val charactersDF = ioAsync { EternalReturnDakGGApi.Data.GetCharacters.execute() }
            val tierDF = ioAsync { EternalReturnDakGGApi.Data.GetTiers.execute() }
            val seasonDF = ioAsync { EternalReturnDakGGApi.Data.GetCurrentSeason.execute() }
            val gamesDF = ioAsync { EternalReturnOpenApi.Game.GetGamesByUserId(userId).execute() }
            Quint(profileDF.await(), charactersDF.await(), tierDF.await(), seasonDF.await(), gamesDF.await())
        }

        val playerSeasonOverviews = profile.playerSeasonOverviews
        val playerSeasonOverview =
            playerSeasonOverviews.firstOrNull { it.matchingModeId == matchingMode.value } ?: run {
                playerSeasonOverviews.firstOrNull()
            }

        val accountLevel = profile.player.accountLevel
        val profileImageUrl = run {
            val characterState = playerSeasonOverview?.characterStats?.first()
                ?: playerSeasonOverviews.firstOrNull { it.characterStats.isNotEmpty() }?.characterStats?.first()
            val skinState = characterState?.skinStats?.first()
            return@run if (characterState != null && skinState != null) {
                ImageResourcesType.CharacterResult.getCharacterPath(
                    characterState.key.toInt(),
                    skinState.key
                )
            } else {
                "/static/images/character-null.png"
            }

        }


        /**
         * 近期一起玩的人
         */
        val recentPlays = mutableListOf<EternalReturnPlayRender.EternalReturnPlayerRecentPlay>()

        playerSeasonOverviews.firstOrNull { seasonOverview -> seasonOverview.duoStats.isNotEmpty() }
            ?.let { seasonOverview ->
                seasonOverview.duoStats.take(8).forEach { duoStat ->
                    val characterById = characters.getCharacterById(duoStat.characterStats.first().key)
                    recentPlays.add(EternalReturnPlayRender.EternalReturnPlayerRecentPlay().apply {
                        imageWrapperUrl = ImageResourcesType.CharacterProfile.getCharacterPath(
                            characterById.id.toInt(), characterById.skins.first().id
                        )
                        this.plays = duoStat.play
                        val playDouble = this.plays.toDouble()
                        this.nickname = nicknameHide(duoStat.nickname)
                        this.winRate = "${String.format("%.1f", (duoStat.win / playDouble) * 100)}%"
                        this.avgRank = "#${String.format("%.1f", duoStat.place / playDouble)}"
                    })
                }
            }

        val eternalReturnPlayerData = EternalReturnPlayRender.EternalReturnPlayerData()


        /**
         * 段位收集
         */
        var tier: DakGGTiersResponse.EternalReturnTier = tiers.getUnRank()
        val latestPlaySeason = profile.playerSeasons.firstOrNull() ?: run {
            throw MessageReplyException("该玩家无任何游玩数据".toText())
        }


        /**
         * 指定的就是排位
         */
        if (matchingMode == MatchingMode.Rank) {
            tier = tiers.getTierById(latestPlaySeason.tierId)
        }


        /**
         * 左边栏段位显示
         */
        eternalReturnPlayerData.tierImageUrl = ImageResourcesType.TierRound.getGeneralPath(tier.id.toString())
        if (matchingMode == MatchingMode.Rank) {
            eternalReturnPlayerData.rpName = tier.name
            eternalReturnPlayerData.rp =
                if (latestPlaySeason.mmr == 0) "段位鉴定中." else latestPlaySeason.mmr.toString()
            eternalReturnPlayerData.tierImageUrl = ImageResourcesType.TierRound.getGeneralPath(tier.id.toString())
        } else {
            eternalReturnPlayerData.rpName = "非排位数据"
            eternalReturnPlayerData.rp = "无"
        }


        /**
         * 左边栏数据展示
         */
        if (playerSeasonOverview !== null) {
            val playDouble = playerSeasonOverview.play.toDouble()
            eternalReturnPlayerData.play = playerSeasonOverview.play
            eternalReturnPlayerData.avgTk = String.format("%.2f", playerSeasonOverview.teamKill / playDouble)
            eternalReturnPlayerData.avgKill = String.format("%.2f", playerSeasonOverview.playerKill / playDouble)
            eternalReturnPlayerData.avgRank = "#" + String.format("%.2f", playerSeasonOverview.place / playDouble)
            eternalReturnPlayerData.avgDmg =
                (playerSeasonOverview.damageToPlayer / playerSeasonOverview.play).toString()
            eternalReturnPlayerData.avgAssists =
                String.format("%.2f", playerSeasonOverview.playerAssistant / playDouble)
            eternalReturnPlayerData.top1 = String.format("%.1f", (playerSeasonOverview.win / playDouble) * 100) + "%"
            eternalReturnPlayerData.top2 = String.format("%.1f", (playerSeasonOverview.top2 / playDouble) * 100) + "%"
            eternalReturnPlayerData.top3 = String.format("%.1f", (playerSeasonOverview.top3 / playDouble) * 100) + "%"
        }

        /**
         * 左边栏分数波动
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

        /**
         * 左边栏常用角色
         */

        val characterUseStats = mutableListOf<EternalReturnPlayRender.EternalReturnCharacterUseStats>()
        playerSeasonOverviews.firstOrNull { it.matchingModeId == 3 }?.characterStats?.take(8)
            ?.forEach { characterState ->
                val characterById = characters.getCharacterById(characterState.key)
                characterUseStats.add(
                    EternalReturnPlayRender.EternalReturnCharacterUseStats(
                        characterName = characterById.name,
                        imgUrl = ImageResourcesType.CharacterProfile.getCharacterPath(
                            characterById.id.toInt(), characterById.skins.first().id
                        ),
                        winRate = "${
                            String.format(
                                "%.1f",
                                if (characterState.win == 0L) 0.0 else characterState.win / characterState.play.toDouble() * 100
                            )
                        }%",
                        characterPlay = characterState.play,
                        getRP = characterState.mmrGain,
                        avgRank = "#${
                            String.format(
                                "%.1f", characterState.place / characterState.play.toDouble()
                            )
                        }",
                        avgDmg = if (characterState.damageToPlayer == 0) 0 else characterState.damageToPlayer / characterState.play,
                    )
                )
            }

        return EternalReturnPlayRender(
            mmrStats = playerMMRStats,
            nickName = nicknameHide(nickname),
            profileImageUrl = profileImageUrl,
            level = accountLevel,
            data = eternalReturnPlayerData,
            matches = gamesResponse.userGames.map { gameConvertMatcher(it, characters) },
            recentPlayers = recentPlays,
            characterUseStats = characterUseStats,
            // 待修改
            season = season.name,
            mode = matchingMode.modeName
        )
    }

    val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
    private fun gameConvertMatcher(
        game: UserGame,
        characters: DakGGCharactersResponse,
    ): EternalReturnPlayRender.EternalReturnPlayerMatchData {
        val killAndAssist = game.playerKill + game.playerAssistant
        val date = ZonedDateTime.parse(game.startDtm, dateFormatter).plusDays(-1)
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
            characterAvatarUrl = ImageResourcesType.CharacterProfile.getCharacterPath(
                game.characterNum.toInt(),
                game.skinCode
            ),
            dateHour = "${date.hour}:${date.minute}:${date.second}",
            dateMonth = "${date.monthValue}月${date.dayOfMonth}日",
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
            val leaderboardDeferred = ioAsync {
                val type = EternalReturnDakGGApi.Data.GetCurrentSeason.execute().type
                EternalReturnDakGGApi.Leaderboard.GetLeaderboard(1, type, serverName, DakGGTeamMode.Squad).execute()
            }
            val tierDistributionDeferred = ioAsync {
                EternalReturnDakGGApi.Statistics.GetTierDistribution(DakGGTeamMode.Squad).execute()
            }
            val seasonDF = ioAsync { EternalReturnDakGGApi.Data.GetCurrentSeason.execute() }
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
                EternalReturnOpenApi.User.GetIdByNickName(nickname).execute()
            }
            val dataCurrentSeasonDF = ioAsync {
                EternalReturnOpenApi.Data.GetGameDataBySeason.execute()
            }
            userResponseDF.await() to dataCurrentSeasonDF.await()
        }
        val userId = userResponse.user.userId
        val seasonID = dataCurrentSeason.seasonID
        val userStatsResponses = coroutineScope {
            val list = CopyOnWriteArrayList<UserStatsResponse>()
            for (i in 1..<seasonID) {
                ioLaunch {
                    // TODO 缓存来自底层 在没有缓存的情况下会同时发送大量请求
                    val resp = EternalReturnOpenApi.User.GetUserStats(
                        userId,
                        i,
                        MatchingMode.Rank
                    ).execute()
                    list.add(resp)
                }
            }
            list
        }

        val oldNames = mutableSetOf<String>()
        for (response in userStatsResponses) {
            response.user.firstOrNull()?.nickname?.let { oldName ->
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