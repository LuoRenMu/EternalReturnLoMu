package cn.luorenmu.request.api.entity.response.dakgg

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * dak.gg 角色详情页（tab=introduction）`__NEXT_DATA__` 的解析模型。
 *
 * 页面统计藏于 `dehydratedState.queries[].state.data` 中带 `characterDetailStatSnapshot`
 * 的那个 query，其余字段均忽略（Json 配置 ignoreUnknownKeys）。
 * 字段命名对齐 Rust 参考实现（serde camelCase）。
 *
 * @author LoMu
 * Date 2026/8/8
 */
@Serializable
data class CharacterAnalysisResponse(
    val props: Props = Props(),
) {
    @Serializable
    data class Props(
        @SerialName("pageProps")
        val pageProps: PageProps = PageProps(),
    )

    @Serializable
    data class PageProps(
        @SerialName("characterTitle")
        val characterTitle: String = "",
        val character: Character = Character(),
        @SerialName("dehydratedState")
        val dehydratedState: DehydratedState = DehydratedState(),
    )

    @Serializable
    data class Character(
        val id: Long = 0,
        val key: String = "",
        val name: String = "",
        @SerialName("charArcheTypes")
        val charArcheTypes: List<String> = emptyList(),
    )

    @Serializable
    data class DehydratedState(
        val queries: List<Query> = emptyList(),
    )

    @Serializable
    data class Query(
        val state: State = State(),
    )

    @Serializable
    data class State(
        val data: Data = Data(),
    )

    @Serializable
    data class Data(
        val meta: Meta? = null,
        val patches: List<Long> = emptyList(),
        @SerialName("characterDetailStatSnapshot")
        val characterDetailStatSnapshot: Snapshot? = null,
        @SerialName("maxSkillRankBySlot")
        val maxSkillRankBySlot: MaxSkillRankBySlot? = null,
        val players: List<Player> = emptyList(),
        @SerialName("playerTiers")
        val playerTiers: List<PlayerTier> = emptyList(),
    )

    @Serializable
    data class Meta(
        @SerialName("updatedAt")
        val updatedAt: Long = 0,
    )

    @Serializable
    data class MaxSkillRankBySlot(
        @SerialName("Q") val q: Long = 0,
        @SerialName("W") val w: Long = 0,
        @SerialName("E") val e: Long = 0,
        @SerialName("R") val r: Long = 0,
        @SerialName("T") val t: Long = 0,
    )

    @Serializable
    data class Player(
        @SerialName("userNum")
        val userNum: Long = 0,
        val name: String = "",
    )

    @Serializable
    data class PlayerTier(
        @SerialName("userNum")
        val userNum: Long = 0,
        val mmr: Long = 0,
        @SerialName("tierId")
        val tierId: Int = 0,
    )

    @Serializable
    data class Snapshot(
        val patch: Long = 0,
        val tier: String = "",
        @SerialName("matchingMode")
        val matchingMode: Long = 0,
        @SerialName("teamMode")
        val teamMode: Long = 0,
        @SerialName("tierCount")
        val tierCount: Long = 0,
        @SerialName("tierGameCount")
        val tierGameCount: Long = 0,
        @SerialName("characterDetailStat")
        val characterDetailStat: DetailStat = DetailStat(),
    )

    @Serializable
    data class DetailStat(
        val key: Long = 0,
        val count: Long = 0,
        @SerialName("weaponStats")
        val weaponStats: List<WeaponStat> = emptyList(),
    )

    @Serializable
    data class WeaponStat(
        val key: Int = 0,
        val count: Long = 0,
        val win: Long = 0,
        val top3: Long = 0,
        val place: Long = 0,
        @SerialName("playerKill")
        val playerKill: Long = 0,
        @SerialName("mmrGain")
        val mmrGain: Long = 0,
        val tier: String = "",
        @SerialName("tierScore")
        val tierScore: Double? = null,
        val rank: RankStat? = null,
        @SerialName("skillBuildStats")
        val skillBuildStats: List<SkillBuildStat> = emptyList(),
        @SerialName("itemBuildStats")
        val itemBuildStats: List<ItemBuildStat> = emptyList(),
        @SerialName("tacticalSkillStats")
        val tacticalSkillStats: List<CountStat> = emptyList(),
        @SerialName("traitCoreStats")
        val traitCoreStats: List<TraitCoreStat> = emptyList(),
        @SerialName("infusionStats")
        val infusionStats: List<CountStat> = emptyList(),
    )

    @Serializable
    data class RankStat(
        val count: Long = 0,
        val size: Long = 0,
    )

    @Serializable
    data class SkillBuildStat(
        val key: String = "",
        val count: Long = 0,
        val win: Long = 0,
        @SerialName("orderStats")
        val orderStats: List<OrderStat> = emptyList(),
    )

    @Serializable
    data class ItemBuildStat(
        val key: List<Long> = emptyList(),
        val count: Long = 0,
        val win: Long = 0,
        @SerialName("orderStats")
        val orderStats: List<OrderStat> = emptyList(),
    )

    @Serializable
    data class OrderStat(
        val key: String = "",
        val count: Long = 0,
    )

    @Serializable
    data class TraitCoreStat(
        val key: Long = 0,
        val count: Long = 0,
        val win: Long = 0,
        val stats: List<CountStat> = emptyList(),
    )

    @Serializable
    data class CountStat(
        val key: Long = 0,
        val count: Long = 0,
        val win: Long = 0,
    )
}
