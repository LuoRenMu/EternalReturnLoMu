package cn.luorenmu.service

import cn.luorenmu.exception.MessageReplyException
import cn.luorenmu.request.api.entity.module.ImageResourcesType
import cn.luorenmu.request.api.entity.response.dakgg.*
import cn.luorenmu.request.api.entity.response.game.BattleUserGamesResponse.UserGame
import cn.luorenmu.request.entity.module.MatchingMode
import cn.luorenmu.service.entity.EternalReturnEquip
import cn.luorenmu.service.entity.EternalReturnPlayRender
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * @author LoMu
 * Date 2025/11/21 14:20
 */
class PlayerRenderAssembler {


    fun assemble(
        profile: DakGGProfileResponse,
        games: MutableList<UserGame>,
        characters: DakGGCharactersResponse,
        tiers: DakGGTiersResponse,
        season: DakGGCurrentSeasonResponse,
        matchingMode: MatchingMode,
        nickname: String,
    ): EternalReturnPlayRender = buildRender(profile, games, characters, tiers, season, matchingMode, nickname)

    private fun buildRender(
        profile: DakGGProfileResponse,
        games: MutableList<UserGame>,
        characters: DakGGCharactersResponse,
        tiers: DakGGTiersResponse,
        season: DakGGCurrentSeasonResponse,
        matchingMode: MatchingMode,
        nickname: String,
    ): EternalReturnPlayRender {
        if (games.isEmpty()) {
            throw MessageReplyException("该玩家无任何游玩数据")
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
                ImageResourcesType.Character.getCharacterPath(
                    characterState.key.toInt(),
                    skinState.key, DakGGCharacterImgType.CharResult
                )
            } else {
                "/static/images/character-null.png"
            }
        }

        val recentPlays = mutableListOf<EternalReturnPlayRender.EternalReturnPlayerRecentPlay>()
        playerSeasonOverviews.firstOrNull { seasonOverview -> seasonOverview.duoStats.isNotEmpty() }
            ?.let { seasonOverview ->
                seasonOverview.duoStats.take(8).forEach { duoStat ->
                    val characterById = characters.getCharacterById(duoStat.characterStats.first().key)
                    recentPlays.add(EternalReturnPlayRender.EternalReturnPlayerRecentPlay().apply {
                        imageWrapperUrl = ImageResourcesType.Character.getCharacterPath(
                            characterById.id.toInt(), characterById.skins.first().id, DakGGCharacterImgType.CharProfile
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
        var tier: DakGGTiersResponse.EternalReturnTier = tiers.getUnRank()

        val latestPlaySeason = profile.playerSeasons.firstOrNull() ?: run {
            throw MessageReplyException("该玩家无任何赛季有游玩数据")
        }

        if (matchingMode == MatchingMode.Rank) {
            tier = tiers.getTierById(latestPlaySeason.tierId)
        }
        val tierGradeId = latestPlaySeason.tierGradeId
        val tierMmr = latestPlaySeason.tierMmr

        eternalReturnPlayerData.tierImageUrl = ImageResourcesType.TierRound.getGeneralPath(tier.id.toString())
        if (matchingMode == MatchingMode.Rank) {
            when (tier.id) {
                // 8 永恒
                8 -> {
                    val rankArea = playerSeasonOverviews.first { it.rank != null }.rank!!
                    eternalReturnPlayerData.rpName = "${tier.name} - 第${rankArea.global.rank}名"

                }
                // 7 半神
                7 -> {
                    val rankArea = playerSeasonOverviews.first { it.rank != null }.rank!!
                    eternalReturnPlayerData.rpName = "${tier.name} - 第${rankArea.global.rank}名"
                }

                else -> {
                    eternalReturnPlayerData.rpName = "${tier.name} $tierGradeId - $tierMmr"
                }
            }

            eternalReturnPlayerData.rp =
                if (latestPlaySeason.mmr == 0) "段位鉴定中." else latestPlaySeason.mmr.toString()
            eternalReturnPlayerData.tierImageUrl = ImageResourcesType.TierRound.getGeneralPath(tier.id.toString())
        } else {
            eternalReturnPlayerData.rpName = "非排位数据"
            eternalReturnPlayerData.rp = "无"
        }

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
            eternalReturnPlayerData.avgAnimal = String.format("%.2f", playerSeasonOverview.monsterKill / playDouble)
            eternalReturnPlayerData.avgCredit =
                String.format("%.2f", playerSeasonOverview.totalGainVFCredit / playDouble)
            eternalReturnPlayerData.avgVision =
                String.format("%.2f", playerSeasonOverview.viewContribution / playDouble)
        }

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

        val characterUseStats = mutableListOf<EternalReturnPlayRender.EternalReturnCharacterUseStats>()
        playerSeasonOverviews.firstOrNull { it.matchingModeId == 3 }?.characterStats?.take(8)
            ?.forEach { characterState ->
                val characterById = characters.getCharacterById(characterState.key)
                characterUseStats.add(
                    EternalReturnPlayRender.EternalReturnCharacterUseStats(
                        characterName = characterById.name,
                        imgUrl = ImageResourcesType.Character.getCharacterPath(
                            characterById.id.toInt(), characterById.skins.first().id, DakGGCharacterImgType.CharProfile
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
        val summary = if (playerSeasonOverviews.firstOrNull { it.teamModeId == 3 && it.matchingModeId == 3 } != null) {
            val pso = playerSeasonOverviews.first { it.teamModeId == 3 && it.matchingModeId == 3 }
            if (pso.recentMatches.isNotEmpty()) {
                val recentMatches = pso.recentMatches
                EternalReturnPlayRender.EternalReturnSummary(
                    count = pso.recentMatches.size,
                    avgRank = String.format("%.1f", recentMatches.map { it.gameRank.toDouble() }.average()),
                    wins = recentMatches.filter { it.gameRank == 1 }.size.toString(),
                    avgTk = String.format("%.1f", recentMatches.map { it.teamKill.toDouble() }.average()),
                    ranks = recentMatches.map { it.gameRank }.toList(),
                    avgDmg = recentMatches.map { it.damageToPlayer.toDouble() }.average().toString(),

                    )
            } else null
        } else null
        return EternalReturnPlayRender(
            mmrStats = playerMMRStats,
            nickName = nicknameHide(nickname),
            profileImageUrl = profileImageUrl,
            level = accountLevel,
            data = eternalReturnPlayerData,
            matches = games.map { gameConvertMatcher(it, characters) },
            recentPlayers = recentPlays,
            characterUseStats = characterUseStats,
            season = season.name,
            mode = matchingMode.modeName,
            summary = summary
        )
    }

    private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ")

    private fun gameConvertMatcher(
        game: UserGame,
        characters: DakGGCharactersResponse,
    ): EternalReturnPlayRender.EternalReturnPlayerMatchData {
        val killAndAssist = game.playerKill + game.playerAssistant
        val date = ZonedDateTime.parse(game.startDtm, dateFormatter).plusHours(-1)
        val now = ZonedDateTime.now()
        return EternalReturnPlayRender.EternalReturnPlayerMatchData(
            level = game.characterLevel.toInt(),
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
            characterAvatarUrl = ImageResourcesType.Character.getCharacterPath(
                game.characterNum.toInt(),
                game.skinCode, DakGGCharacterImgType.CharProfile
            ),
            dateHour = "${String.format("%02d", date.hour)}:${
                String.format("%02d", date.minute)
            }:${String.format("%02d", date.second)}",
            dateMonth = if (isSameDay(date, now)) "今天" else "${date.monthValue}月${date.dayOfMonth}日",
            assist = game.playerAssistant,
            gameId = game.gameId.toString(),
            dmg = game.damageToPlayer,
            kda = if (game.playerDeaths == 0) killAndAssist.toDouble()
            else killAndAssist.toDouble() / game.playerDeaths,
            routeId = if (game.routeIdOfStart != 0L) game.routeIdOfStart.toString() else "Private",
            version = "${game.versionMajor}.${game.versionMinor}"
        )
    }

    private fun isSameDay(date1: ZonedDateTime, date2: ZonedDateTime): Boolean {
        return date1.year == date2.year && date1.month == date2.month && date1.dayOfMonth == date2.dayOfMonth
    }

    private fun gameEquip(game: UserGame): MutableList<EternalReturnEquip> {
        val equipList = mutableListOf<EternalReturnEquip>()
        for ((index, value) in game.equipmentReal) {
            val equip = EternalReturnEquip(
                itemBgUrl = ImageResourcesType.ItemBg.getGeneralPath(game.equipmentGradeReal[index].toString()),
                itemUrl = ImageResourcesType.Item.getGeneralPath(value.toString())
            )
            equipList.add(equip)
        }
        return equipList
    }

    private fun nicknameHide(nickname: String): String {
        val length = nickname.length
        return if (length < 3) {
            nickname.replace(nickname.substring(1, length), " * ")
        } else {
            nickname.replace(nickname.substring(1, length - 1), " * ".repeat(length - 2))
        }
    }
}
