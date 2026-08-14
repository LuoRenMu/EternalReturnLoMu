package cn.luorenmu.service.entity

import cn.luorenmu.HTTP_SERVER_URL

/**
 * 角色详情渲染实体（对应 bserAnalysis CharacterDetail.tsx 的 CharacterDetailRender）。
 *
 * 展示字符串在 service 层预计算（百分比/相对时间/段位标签等），FTL 直接引用。
 * 图片一律使用 cdn.dak.gg 的 CDN URL。
 *
 * @author LoMu
 * Date 2026/8/8
 */
data class CharacterDetail(
    val id: Long,
    val name: String,
    val title: String,
    /** 角色 CharResult 大图（CDN） */
    val imageUrl: String,
    val archetypes: List<String>,
    val analysis: CharacterAnalysis?,
    val httpServer: String = HTTP_SERVER_URL,
) {
    data class CharacterAnalysis(
        /** dak.gg 段位 key，如 "diamond_plus" */
        val tier: String,
        /** 段位中文标签，如 "灭钻" */
        val tierLabel: String,
        val matchingModeLabel: String,
        val teamModeLabel: String,
        /** 相对更新时间，如 "3小时前" */
        val updatedLabel: String,
        /** 版本号，如 "3 / 4" */
        val patchLabel: String,
        val totalGames: Long,
        val characterGames: Long,
        /** 角色登场率（0-100） */
        val pickRate: Double,
        /** 角色梯度字母（S/A/B/...），取登场最高武器 */
        val characterTier: String,
        /** 各武器流派（按登场降序，首个为主流派） */
        val weapons: List<WeaponBuild>,
        val topPlayers: List<TopPlayer>,
    )

    data class WeaponBuild(
        val weaponId: Long,
        val weapon: String,
        val iconUrl: String,
        val tier: String,
        val tierScore: Double,
        /** 平均每局 RP 变动 */
        val rpChange: Double,
        val games: Long,
        val pickRate: Double,
        val winRate: Double,
        val top3Rate: Double,
        val avgRank: Double,
        val avgKills: Double,
        /** 登场数在全角色武器中的名次 */
        val rank: Long,
        val rankSize: Long,
        /** 该角色五个技能槽图鉴（Q/W/E/R/T） */
        val skills: List<SkillSlot>,
        /** 技能槽 → 图鉴 快速查找（供 FTL 按槽位取图标） */
        val skillBySlot: Map<String, SkillSlot>,
        val skillBuilds: List<SkillBuild>,
        val topEquipments: List<EquipmentSlotPick>,
        val itemBuilds: List<ItemBuild>,
        val tacticals: List<Pick>,
        val augments: List<Augment>,
        /** 钴协议灌注选择率（排位为空） */
        val infusions: List<Pick>,
    )

    data class SkillSlot(
        val id: Long,
        val slot: String,
        val name: String,
        val iconUrl: String,
    )

    data class SkillBuild(
        /** 满级优先顺序，如 ["T","E","W","Q"] */
        val priority: List<String>,
        /** 1-15 级加点顺序 */
        val order: List<String>,
        val pickRate: Double,
        val winRate: Double,
    )

    data class EquipmentSlotPick(
        val slot: String,
        val id: Long,
        val name: String,
        val iconUrl: String,
        val bgUrl: String,
        val pickRate: Double,
        val winRate: Double,
    )

    data class ItemBuild(
        /** 整套装备（[武器,胸甲,头部,手臂,腿部]） */
        val items: List<Pick>,
        /** 前期出装顺序 */
        val order: List<Pick>,
        val pickRate: Double,
        val winRate: Double,
    )

    data class Pick(
        val id: Long,
        val name: String,
        val iconUrl: String,
        /** 物品品级背景图（CDN），非物品为空 */
        val bgUrl: String,
        val pickRate: Double,
        val winRate: Double,
    )

    data class Augment(
        val core: Pick,
        val subs: List<Pick>,
    )

    data class TopPlayer(
        val name: String,
        val mmr: Long,
        val tierName: String,
        val tierIconUrl: String,
    )
}
