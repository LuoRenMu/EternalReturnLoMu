package cn.luorenmu.request.api.entity.response.game

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 *
 * @author LoMu
 * Date 2025/10/25 17:47
 */

/**
 * 该类表示指定玩家的最近游戏对局信息
 * 该类表示指定对局的玩家对局信息
 */
@Serializable
data class BattleUserGamesResponse(
    val next: Int?,
    val code: Int,
    val message: String,
    val userGames: MutableList<UserGame> = mutableListOf(),
) {
    @Serializable
    data class UserGame(
        // 装备
        // https://cdn.dak.gg/assets/er/game-assets/1.44.0/ItemIcon_115504.png
        @SerialName("equipment")
        val equipment: MutableMap<String, Int>,
        // 装备背景
        // https://cdn.dak.gg/er/images/item/ico-itemgradebg-04.svg
        @SerialName("equipmentGrade")
        val equipmentGrade: MutableMap<String, Int>,
        val userNum: Long = 0,
        val nickname: String = "",
        val gameId: Long = 0,
        val seasonId: Long = 0,
        val matchingMode: Int = 0,
        val matchingTeamMode: Long = 0,
        val characterNum: Long = 0,
        val skinCode: Long = 0,
        val characterLevel: Long = 0,
        val squadRumbleRank: Int = 0,
        val gameRank: Int = 0,
        val playerKill: Int = 0,
        val playerDeaths: Int = 0,
        val playerAssistant: Int = 0,
        val accountLevel: Int = 0,
        val monsterKill: Long = 0,
        /**
         * 这局之前的分数
         */
        val mmrBefore: Int = 0,
        // 这局之后的分数
        val mmrAfter: Int = 0,
        // 这局加了/减了多少分
        val mmrGain: Int = 0,
        // 武器
        val bestWeapon: Int = 0,
        val bestWeaponLevel: Int = 0,
        val masteryLevel: Map<String, Long>,
        val versionMajor: Long = 0,
        val versionMinor: Long = 0,
        val serverName: String = "",
        val criticalStrikeDamage: Double = 0.0,
        val coolDownReduction: Double = 0.0,
        val lifeSteal: Double = 0.0,
        val normalLifeSteal: Double = 0.0,
        val skillLifeSteal: Double = 0.0,
        val amplifierToMonster: Double = 0.0,
        val bonusExp: Long = 0,
        val startDtm: String = "",
        val duration: Long = 0,
        val playTime: Long = 0,
        val watchTime: Long = 0,
        val totalTime: Long = 0,
        val survivableTime: Long = 0,
        val botAdded: Long = 0,
        val botRemain: Long = 0,
        val restrictedAreaAccelerated: Long = 0,
        val safeAreas: Long = 0,
        val teamNumber: Long = 0,
        val preMade: Long = 0,
        val gainedNormalMmrKFactor: Double = 0.0,
        val victory: Long = 0,
        val craftUncommon: Long = 0,
        val craftRare: Long = 0,
        val craftEpic: Long = 0,
        val craftLegend: Long = 0,
        val damageToPlayer: Long = 0,
        val damageFromPlayerItemSkill: Long = 0,
        val damageFromPlayerDirect: Long = 0,
        val damageFromPlayerUniqueSkill: Long = 0,
        val healAmount: Long = 0,
        val teamRecover: Long = 0,
        val protectAbsorb: Long = 0,
        val addSurveillanceCamera: Long = 0,
        val addTelephotoCamera: Long = 0,
        val removeSurveillanceCamera: Long = 0,
        val removeTelephotoCamera: Long = 0,
        val useHyperLoop: Long = 0,
        val useSecurityConsole: Long = 0,
        val giveUp: Long = 0,
        val teamSpectator: Long = 0,
        val pcCafe: Long = 0,
        val routeIdOfStart: Long = 0,
        val routeSlotId: Long = 0,
        val placeOfStart: String = "",
        val matchSize: Long = 0,
        val teamKill: Int = 0,
        val fishingCount: Long = 0,
        val useEmoticonCount: Long = 0,
        val expireDtm: String = "",
        /**
         * 主要天赋技能
         */
        val traitFirstCore: Long = 0,
        val traitSecondSub: List<Long>,
        val rankPoint: Int = 0,
        val scoredPoint: List<Long>,
        val killDetails: String = "",
        val deathDetails: String = "",
        val deathsPhaseOne: Long = 0,
        val deathsPhaseTwo: Long = 0,
        val deathsPhaseThree: Long = 0,
        val usedPairLoop: Long = 0,
        val ccTimeToPlayer: Double = 0.0,
        val creditSource: Map<String, Double>?,
        val boughtInfusion: String = "",
        val itemTransferredConsole: List<Long>,
        val itemTransferredDrone: List<Long>,
        val escapeState: Int = 0,
        val totalExtraKill: Long = 0,
        val collectItemForLog: List<Long>,
        val equipFirstItemForLog: Map<String, List<Long>>,
        /**
         *   战术技能  闪灵、赤色风暴
         */
        val tacticalSkillGroup: Long = 0,
        val tacticalSkillLevel: Long = 0,
        val teamDown: Long = 0,
        val teamBattleZoneDown: Long = 0,
        val teamRepeatDown: Long = 0,
        val skillAmp: Long = 0,
        val isLeavingBeforeCreditRevivalTerminate: Boolean,
        val mmrGainInGame: Long = 0,
        val mmrLossEntryCost: Long = 0,
    )
}