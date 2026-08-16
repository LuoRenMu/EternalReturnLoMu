package cn.luorenmu.plugins.player

import cn.luorenmu.exception.MessageReplyException
import cn.luorenmu.request.api.entity.module.ImageResourcesType
import cn.luorenmu.request.api.entity.response.dakgg.*
import cn.luorenmu.request.api.entity.response.game.BattleUserGamesResponse.UserGame
import cn.luorenmu.request.entity.module.MatchingMode
import cn.luorenmu.service.entity.EternalReturnEquip
import cn.luorenmu.service.entity.EternalReturnPlayRender
import kotlinx.serialization.json.Json
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * @author LoMu
 * Date 2025/11/21 14:20
 */
open class PlayerRenderAssembler {

    private val infusionJson = Json { ignoreUnknownKeys = true }

    fun assemble(
        profile: DakGGProfileResponse,
        games: MutableList<UserGame>,
        characters: DakGGCharactersResponse,
        tiers: DakGGTiersResponse,
        season: DakGGCurrentSeasonResponse,
        infusions: DakGGInfusionsResponse,
        matchingMode: MatchingMode,
        nickname: String,
        characterStats: DakGGCharacterStatsResponse? = null,
    ): EternalReturnPlayRender = buildRender(
        profile, games, characters, tiers, season, infusions, matchingMode, nickname, characterStats,
    )

    private fun buildRender(
        profile: DakGGProfileResponse,
        games: MutableList<UserGame>,
        characters: DakGGCharactersResponse,
        tiers: DakGGTiersResponse,
        season: DakGGCurrentSeasonResponse,
        infusions: DakGGInfusionsResponse,
        matchingMode: MatchingMode,
        nickname: String,
        characterStats: DakGGCharacterStatsResponse?,
    ): EternalReturnPlayRender {
        if (games.isEmpty()) {
            throw MessageReplyException("该玩家无任何游玩数据")
        }

        val playerSeasonOverviews = profile.playerSeasonOverviews
        val playerSeasonOverview =
            playerSeasonOverviews.firstOrNull { it.matchingModeId == matchingMode.value }

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
                        this.nickname = duoStat.nickname
                        this.winRate = "${String.format("%.1f", (duoStat.win / playDouble) * 100)}%"
                        this.avgRank = "#${String.format("%.1f", duoStat.place / playDouble)}"
                    })
                }
            }

        val eternalReturnPlayerData = EternalReturnPlayRender.EternalReturnPlayerData()
        var tier: DakGGTiersResponse.EternalReturnTier = tiers.getUnRank()

        // 最近游玩的赛季
        val latestPlaySeason = profile.playerSeasons?.firstOrNull() ?: run {
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
                    eternalReturnPlayerData.rpName = "${tier.name} - 第${rankArea.global?.rank}名"

                }
                // 7 半神
                7 -> {
                    val rankArea = playerSeasonOverviews.first { it.rank != null }.rank!!
                    eternalReturnPlayerData.rpName = "${tier.name} - 第${rankArea.global?.rank}名"
                }

                else -> {
                    if (latestPlaySeason.mmr == 0) {
                        eternalReturnPlayerData.rpName = "段位未鉴定"
                    }else {
                        eternalReturnPlayerData.rpName = "${tier.name} $tierGradeId - $tierMmr"
                    }
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
                    avgDmg =  String.format("%.1f",recentMatches.map { it.damageToPlayer.toDouble() }.average()),

                    )
            } else null
        } else null

        // 下载与渲染必须使用同一场游戏的赛季，否则非当前赛季玩家会引用未下载的 banner。
        val bannerSeasonId = ImageResourcesType.resolveBannerSeasonId(games.map { it.seasonId }, season.id)
        val bannerUrl = ImageResourcesType.bannerPathForSeason(bannerSeasonId)

        return EternalReturnPlayRender(
            mmrStats = playerMMRStats,
            nickName = nickname,
            profileImageUrl = profileImageUrl,
            level = accountLevel,
            data = eternalReturnPlayerData,
            matches = games.map { gameConvertMatcher(it, characters, infusions) },
            recentPlayers = recentPlays,
            characterUseStats = characterUseStats,
            season = season.name,
            mode = matchingMode.modeName,
            rate = PlayerPerformanceRater.rate(
                games = games,
                characterStats = characterStats,
                matchingMode = matchingMode,
                playerLevel = accountLevel,
                recentMmr = playerMMRStats?.mmr.orEmpty(),
            ),
            summary = summary,
            bannerUrl = bannerUrl,
        )
    }

    private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ")

    private fun gameConvertMatcher(
        game: UserGame,
        characters: DakGGCharactersResponse,
        infusions: DakGGInfusionsResponse,
    ): EternalReturnPlayRender.EternalReturnPlayerMatchData {
        val killAndAssist = game.playerKill + game.playerAssistant
        val date = ZonedDateTime.parse(game.startDtm, dateFormatter).plusHours(-1)
        val isCobalt = MatchingMode.convert(game.matchingMode) == MatchingMode.Cobalt
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
            tacticalSkillLevel = game.tacticalSkillLevel.toInt(),
            traitSkillUrl = ImageResourcesType.TraitSkill.getGeneralPath(game.traitFirstCore.toString()),
            traitSkillGroupUrl = if (isCobalt)
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
            dateMonth = recentlyDateConvert(date),
            assist = game.playerAssistant,
            gameId = game.gameId.toString(),
            dmg = game.damageToPlayer,
            kda = if (game.playerDeaths == 0) killAndAssist.toDouble()
            else killAndAssist.toDouble() / game.playerDeaths,
            routeId = if (game.routeIdOfStart != 0L) game.routeIdOfStart.toString() else "Private",
            version = "${game.versionMajor}.${game.versionMinor}",
            infusions = if (isCobalt) buildInfusionRow(game.boughtInfusion, infusions) else null,
        )
    }

    /**
     * 解析 boughtInfusion JSON（infusionId → 数量），仅保留 Trait 类型，按数量降序取前 3。
     * 固定填充 3 个槽位：不足时 imageUrl 为空、count 为 0。
     */
    private fun buildInfusionRow(
        raw: String,
        infusions: DakGGInfusionsResponse,
    ): List<EternalReturnPlayRender.EternalReturnPlayerMatchData.EternalReturnPlayerInfusion> {
        if (raw.isBlank()) {
            return List(3) { EternalReturnPlayRender.EternalReturnPlayerMatchData.EternalReturnPlayerInfusion() }
        }
        val entries: List<Pair<Long, Long>> = try {
            val map = infusionJson.decodeFromString<Map<String, Long>>(raw)
            map.mapNotNull { (idStr, count) ->
                val id = idStr.toLongOrNull() ?: return@mapNotNull null
                id to count
            }
        } catch (_: Exception) {
            return List(3) { EternalReturnPlayRender.EternalReturnPlayerMatchData.EternalReturnPlayerInfusion() }
        }

        val traits = entries
            .mapNotNull { (id, count) ->
                val infusion = infusions.getInfusionById(id)
                if (infusion?.productType == "Trait") infusion.productId to count else null
            }
            .sortedWith(compareByDescending<Pair<Long, Long>> { it.second }.thenByDescending { it.first })
            .take(3)

        return (0 until 3).map { i ->
            traits.getOrNull(i)?.let { (productId, count) ->
                EternalReturnPlayRender.EternalReturnPlayerMatchData.EternalReturnPlayerInfusion(
                    imageUrl = ImageResourcesType.TraitSkill.getGeneralPath(productId.toString()),
                    count = count.toInt(),
                )
            } ?: EternalReturnPlayRender.EternalReturnPlayerMatchData.EternalReturnPlayerInfusion()
        }
    }

    private fun recentlyDateConvert(date1: ZonedDateTime): String {
        val now = ZonedDateTime.now()
        if (isSameDay(now, date1)) {
            return "今天"
        }
        now.minusDays(1)
        if (isSameDay(now, date1)) {
            return "昨天"
        }
        now.minusDays(1)
        if (isSameDay(now, date1)) {
            return "前天"
        }
        return "${date1.monthValue}月${date1.dayOfMonth}日"
    }

    private fun isSameDay(date1: ZonedDateTime, date2: ZonedDateTime) =
        date1.year == date2.year && date1.month == date2.month && date1.dayOfMonth == date2.dayOfMonth

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


}
