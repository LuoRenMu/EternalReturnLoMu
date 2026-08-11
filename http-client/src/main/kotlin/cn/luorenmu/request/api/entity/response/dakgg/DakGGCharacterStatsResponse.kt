package cn.luorenmu.request.api.entity.response.dakgg
import kotlinx.serialization.Serializable
/**
 *
 * @author LoMu
 * Date 2026/5/23 20:10
 */
@Serializable
data class DakGGCharacterStatsResponse (
    val characterStatSnapshot: CharacterStatSnapshot? = null,

){
    @Serializable
    data class CharacterStatSnapshot(
        val dt: Int,              // 数据时间/天数
        val patch: Int,           // 版本号 (11020 = 1.10.2.0)
        val matchingMode: Int,    // 匹配模式 (3 = 排位)
        val teamMode: Int,        // 队伍模式 (3 = 三人 squad)
        val tier: String,         // 段位筛选 ("diamond_plus" = 钻以上)
        val tierCount: Int,       // 该段位总玩家数
        val tierGameCount: Int,   // 该段位总局数
        val characterStats: List<CharacterStat>
    )

    @Serializable
    data class CharacterStat(
        val key: Int,          // 角色ID (88, 2, ...)
        val count: Int,        // 使用次数
        val weaponStats: List<WeaponStat>
    )

    @Serializable
    data class WeaponStat(
        val key: Int,                      // 武器ID (3, ...)
        val count: Int,                    // 使用次数
        val win: Int,                      // 胜利次数
        val top3: Int,                     // 前3次数
        val place: Int,                    // 排名总分
        val playerKill: Int,              // 击杀数
        val playerAssistant: Int,         // 助攻数
        val playerDeaths: Int,            // 死亡数
        val damageToPlayer: Int,          // 对玩家伤害
        val damageToMonster: Long,        // 对怪物伤害 (数值较大用Long)
        val monsterKill: Int,             // 击杀怪物数
        val teamKill: Int,                // 团队击杀
        val mmrGain: Int,                 // MMR增益
        val tier: String,                 // 段位 (如 "S")
        val tierScore: Double? = 0.0,               // 段位分数
        val viewContribution: Int,        // 视野贡献
        val rank: RankStat? = null        // 排名统计 (可能为空)
    )

    @Serializable
    data class RankStat(
        val size: Int,
        val count: Int,
        val win: Int,
        val top3: Int,
        val place: Int,
        val playerKill: Int,
        val playerAssistant: Int,
        val playerDeaths: Int,
        val damageToPlayer: Int,
        val damageToMonster: Int,
        val mmrGain: Int,
        val monsterKill: Int,
        val teamKill: Int,
        val viewContribution: Int
    )
}
