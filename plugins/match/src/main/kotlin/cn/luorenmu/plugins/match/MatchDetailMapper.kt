package cn.luorenmu.plugins.match

import cn.luorenmu.common.util.PathUtils
import cn.luorenmu.exception.MessageReplyException
import cn.luorenmu.request.api.entity.module.ImageResourcesType
import cn.luorenmu.request.api.entity.response.dakgg.DakGGCharacterImgType
import cn.luorenmu.request.api.entity.response.dakgg.DakGGCharactersResponse
import cn.luorenmu.request.api.entity.response.dakgg.DakGGTiersResponse
import cn.luorenmu.request.api.entity.response.game.BattleUserGamesResponse.UserGame
import cn.luorenmu.request.entity.module.MatchingMode
import kotlinx.serialization.json.*
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal data class MatchPlayerTier(val tierId: Int, val gradeId: Int?)
internal data class MatchPlayerExtras(val taken: Long?, val vision: Long?, val credits: Long?)
internal data class MatchSnapshot(
    val games: List<UserGame>,
    val tiers: Map<Long, MatchPlayerTier> = emptyMap(),
    val extras: Map<Long, MatchPlayerExtras> = emptyMap(),
)

/** Keep endpoint-specific fields here, so updating the thin plugin does not require replacing the host DTO. */
internal object MatchDetailMapper {
    private val json = Json { ignoreUnknownKeys = true }

    fun decode(payload: JsonObject, requestedId: Long): MatchSnapshot {
        val rows = payload["matches"] as? JsonArray
            ?: throw MessageReplyException("未找到该对局的结算数据，请稍后重试")
        val games = rows.map { json.decodeFromJsonElement<UserGame>(it) }
        validate(games, requestedId)
        val tiers = (payload["playerTiers"] as? JsonArray).orEmpty().mapNotNull { element ->
            val row = element.jsonObject
            val id = row.long("userNum") ?: return@mapNotNull null
            val tier = row.long("tierId")?.toInt() ?: return@mapNotNull null
            id to MatchPlayerTier(tier, row.long("tierGradeId")?.toInt())
        }.toMap()
        val extras = rows.associate { element ->
            val row = element.jsonObject
            requireNotNull(row.long("userNum")) to MatchPlayerExtras(
                row.long("damageFromPlayer"), row.long("viewContribution"), row.long("totalGainVFCredit"))
        }
        return MatchSnapshot(games, tiers, extras)
    }

    fun validate(games: List<UserGame>, requestedId: Long) {
        if (games.isEmpty()) throw MessageReplyException("该对局暂无结算数据，请稍后重试")
        if (games.any { it.gameId != requestedId } || games.map { it.userNum }.distinct().size != games.size)
            throw MessageReplyException("对局结算数据不完整或 ID 不匹配，请稍后重试")
    }

    fun assemble(
        snapshot: MatchSnapshot,
        nickname: String?,
        characters: DakGGCharactersResponse,
        tiers: DakGGTiersResponse,
    ): MatchDetailData {
        val first = snapshot.games.first()
        // DAK detail IDs are anonymized; focus by nickname, never by the profile's userNum.
        val focused = snapshot.games.firstOrNull { nickname != null && it.nickname.equals(nickname, true) }?.userNum
        return MatchDetailData(
            gameId = first.gameId.toString(),
            mode = MatchingMode.entries.firstOrNull { it.value == first.matchingMode }?.modeName ?: "未知模式",
            server = first.serverName.ifBlank { "未知服务器" },
            startedAt = runCatching {
                val timestamp = Regex("([+-]\\d{2})(\\d{2})$").replace(first.startDtm) {
                    "${it.groupValues[1]}:${it.groupValues[2]}"
                }
                OffsetDateTime.parse(timestamp).atZoneSameInstant(ZoneId.of("Asia/Shanghai"))
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            }.getOrElse { first.startDtm.ifBlank { "时间未知" } },
            durationSeconds = snapshot.games.maxOf { it.duration }.takeIf { it > 0 },
            version = if (first.versionMajor > 0) "${first.versionMajor}.${first.versionMinor}" else "版本未知",
            focusedPlayerId = focused,
            teams = snapshot.games.groupBy { it.teamNumber }.map { (number, games) ->
                MatchTeam(number.toInt(), games.first().gameRankVirtual.takeIf { it > 0 },
                    games.first().teamKill, games.map { game -> player(game, snapshot, characters, tiers) })
            },
        )
    }

    private fun player(game: UserGame, snapshot: MatchSnapshot, characters: DakGGCharactersResponse, tiers: DakGGTiersResponse): MatchPlayer {
        val character = characters.characters.firstOrNull { it.id == game.characterNum }
        val tierInfo = snapshot.tiers[game.userNum]
        val tier = tiers.tiers.firstOrNull { it.id == tierInfo?.tierId }
        val extra = snapshot.extras[game.userNum]
        return MatchPlayer(
            id = game.userNum, nickname = game.nickname, character = character?.name ?: "角色 ${game.characterNum}",
            portrait = character?.let { local(ImageResourcesType.Character.getCharacterPath(
                game.characterNum.toInt(), game.skinCode, DakGGCharacterImgType.CharProfile)) },
            kills = game.playerKill, deaths = game.playerDeaths, assists = game.playerAssistant,
            damage = game.damageToPlayer, taken = extra?.taken,
            // DAK's survivableTime is not elapsed match time (e.g. 40 for a 22-minute game).
            survivalSeconds = game.playTime.takeIf { it > 0 },
            rp = game.mmrAfter.takeIf { game.matchingMode == MatchingMode.Rank.value },
            rpChange = game.mmrGain.takeIf { game.matchingMode == MatchingMode.Rank.value },
            healing = game.healAmount, vision = extra?.vision, credits = extra?.credits,
            equipment = (0..4).map { image(ImageResourcesType.Item, game.equipmentReal[it]?.toLong()) },
            equipmentBackgrounds = (0..4).map { image(ImageResourcesType.ItemBg, game.equipmentGradeReal[it]?.toLong()) },
            tierName = tier?.name?.let { name ->
                val grade = tierInfo?.gradeId?.takeIf { it > 0 }
                if (grade != null && tier.id in 1..6) "$name $grade" else name
            },
            tierIcon = tier?.let { image(ImageResourcesType.TierRound, it.id.toLong()) },
            weaponIcon = image(ImageResourcesType.Weapon, game.bestWeapon.toLong()),
            traitSkillIcon = image(ImageResourcesType.TraitSkill, game.traitFirstCore),
            tacticalSkillIcon = image(ImageResourcesType.TacticalSkill, game.tacticalSkillGroup),
        )
    }

    private fun image(type: ImageResourcesType, id: Long?): String? =
        id?.takeIf { it > 0 }?.let { local(type.getGeneralPath(it.toString())) }

    private fun local(path: String) = PathUtils.resourcesPathResolve(path.removePrefix("/resources/")).toUri().toString()
    private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull
}
