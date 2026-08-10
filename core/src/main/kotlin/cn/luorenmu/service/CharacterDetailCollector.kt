package cn.luorenmu.service

import cn.luorenmu.common.extensions.toPinYin
import cn.luorenmu.exception.MessageReplyException
import cn.luorenmu.request.api.Api.Companion.ioAsync
import cn.luorenmu.request.api.entity.module.ImageResourcesType
import cn.luorenmu.request.api.entity.response.dakgg.CharacterAnalysisResponse
import cn.luorenmu.request.api.entity.response.dakgg.CharacterAnalysisResponse.Data
import cn.luorenmu.request.api.entity.response.dakgg.CharacterAnalysisResponse.WeaponStat
import cn.luorenmu.request.api.entity.response.dakgg.DakGGCharacterImgType
import cn.luorenmu.request.api.entity.response.dakgg.DakGGCharactersResponse
import cn.luorenmu.request.api.entity.response.dakgg.DakGGItemsResponse
import cn.luorenmu.request.api.entity.response.dakgg.DakGGSkillsResponse
import cn.luorenmu.request.api.entity.response.dakgg.DakGGTacticalSkillResponse
import cn.luorenmu.request.api.entity.response.dakgg.DakGGTiersResponse
import cn.luorenmu.request.api.entity.response.dakgg.DakGGTraitSkillsResponse
import cn.luorenmu.request.api.entity.response.dakgg.DakGGWeaponResponse
import cn.luorenmu.request.api.impl.EternalReturnDakGGApi
import cn.luorenmu.request.entity.module.DakGGRank
import cn.luorenmu.service.entity.CharacterDetail
import kotlinx.coroutines.coroutineScope

/**
 * 角色详情分析收集器。
 *
 * 抓取 dak.gg 角色详情页（[EternalReturnDakGGApi.GetCharacterAnalysis]）并从
 * `dehydratedState` 中定位统计快照，叠加角色/武器/段位/物品/战术/强化/技能参考数据，
 * 组装成 [CharacterDetail] 渲染实体（对应 bserAnalysis 的 build_character_analysis / build_weapon）。
 *
 * 图片一律走本地 [ResourcesDownloadService] 下载，实体中存放 `/resources/images/...` 本地路径，
 * FTL 渲染时拼接 `${httpServer}`。
 *
 * @author LoMu
 * Date 2026/8/8
 */
open class CharacterDetailCollector(
    private val resourcesDownloadService: ResourcesDownloadService = ResourcesDownloadService(),
) {

    companion object {
        private const val TOP_PLAYER_LIMIT = 10
        private const val SKILL_BUILD_LIMIT = 3
        private const val ITEM_BUILD_LIMIT = 3
        private const val TACTICAL_LIMIT = 3
        private const val AUGMENT_CORE_LIMIT = 3
        private const val AUGMENT_SUB_LIMIT = 6
        private const val INFUSION_LIMIT = 12
        private val SKILL_SLOTS = listOf("Q", "W", "E", "R", "T")
        private val ITEM_SLOTS = listOf("武器", "胸甲", "头部", "手臂", "腿部")

        private val MATCHING_MODE_LABELS = mapOf(2L to "普通", 3L to "排位", 6L to "钴协议")
        private val TEAM_MODE_LABELS = mapOf(1L to "单人", 2L to "双人", 3L to "三人", 4L to "钴协议")
        private val TIER_LABELS = DakGGRank.entries.associate { it.value to it.shortName }
    }

    open suspend fun collect(
        characterQuery: String,
        teamMode: String,
        matchingMode: String,
        tier: String?,
    ): CharacterDetail {
        val characters = EternalReturnDakGGApi.Data.GetCharacters.execute()
        val character = characters.characters.firstOrNull { c ->
            c.name.toPinYin().trim().equals(characterQuery.toPinYin().trim(), true)
        } ?: characterQuery.toLongOrNull()?.let { id -> characters.characters.firstOrNull { it.id == id } }
            ?: throw MessageReplyException("不存在的角色名称")
        val characterId = character.id

        val refs = coroutineScope {
            val analysisDF = ioAsync {
                EternalReturnDakGGApi.GetCharacterAnalysis(
                    character.key, teamMode, matchingMode, tier
                ).execute()
            }
            val weaponsDF = ioAsync { EternalReturnDakGGApi.Data.GetWeapons.execute() }
            val tiersDF = ioAsync { EternalReturnDakGGApi.Data.GetTiers.execute() }
            val itemsDF = ioAsync { EternalReturnDakGGApi.Data.GetItems.execute() }
            val tacticalsDF = ioAsync { EternalReturnDakGGApi.Data.GetTacticalSkills.execute() }
            val traitsDF = ioAsync { EternalReturnDakGGApi.Data.GetTraitSkills.execute() }
            val skillsDF = ioAsync { EternalReturnDakGGApi.Data.GetSkills.execute() }
            AnalysisRefs(
                characterId = characterId,
                analysis = analysisDF.await(),
                weapons = weaponsDF.await(),
                tiers = tiersDF.await(),
                items = itemsDF.await(),
                tacticals = tacticalsDF.await(),
                traits = traitsDF.await(),
                skills = skillsDF.await(),
            )
        }

        val page = refs.analysis.props.pageProps
        val snapshotData = page.dehydratedState.queries
            .map { it.state.data }
            .firstOrNull { it.characterDetailStatSnapshot != null }

        val detail = CharacterDetail(
            id = characterId,
            name = character.name,
            title = page.characterTitle,
            imageUrl = characterResultPath(character),
            archetypes = page.character.charArcheTypes,
            analysis = snapshotData?.let { buildAnalysis(it, refs) },
        )

        // 预热下载：角色头像、段位图标与最热武器流派用到的全部图片
        detail.analysis?.let { analysis ->
            resourcesDownloadService.downloadTiers(refs.tiers)
            analysis.weapons.firstOrNull()?.let { topWeapon ->
                downloadWeaponImages(topWeapon, character, refs)
            }
        }
        return detail
    }

    /** 下载最热武器流派展示所需的图片（角色头像 + 武器/技能/物品/战术/潜能/灌注）。 */
    private suspend fun downloadWeaponImages(
        weapon: CharacterDetail.WeaponBuild,
        character: DakGGCharactersResponse.DakGGCharacterById,
        refs: AnalysisRefs,
    ) {
        character.skins.firstOrNull()?.let { resourcesDownloadService.downloadCharacterImage(character, it.id) }

        refs.weapons.masteries.firstOrNull { it.id == weapon.weaponId.toInt() }?.let {
            resourcesDownloadService.downloadWeaponImage(it)
        }

        weapon.skills.forEach { slot ->
            refs.skills.findSkill(refs.characterId, slot.slot)?.let {
                resourcesDownloadService.downloadSkillImage(it)
            }
        }

        weapon.itemBuilds.forEach { build ->
            (build.items + build.order).forEach { pick ->
                refs.items.items.firstOrNull { it.id == pick.id }?.let { item ->
                    resourcesDownloadService.downloadItemImage(item)
                    resourcesDownloadService.downloadItemBgImage(itemGradeNum(item.grade))
                }
            }
        }
        weapon.topEquipments.forEach { pick ->
            refs.items.items.firstOrNull { it.id == pick.id }?.let { item ->
                resourcesDownloadService.downloadItemImage(item)
                resourcesDownloadService.downloadItemBgImage(itemGradeNum(item.grade))
            }
        }

        weapon.tacticals.forEach { pick ->
            refs.tacticals.tacticalSkills.firstOrNull { it.id == pick.id }?.let {
                resourcesDownloadService.downloadTacticalSkillImage(it)
            }
        }

        weapon.augments.forEach { aug ->
            (listOf(aug.core) + aug.subs).forEach { pick -> downloadTraitOrProduct(pick.id, refs) }
        }
        weapon.infusions.forEach { pick -> downloadTraitOrProduct(pick.id, refs) }
    }

    /** 潜能/灌注按实体类型下载对应图片。 */
    private suspend fun downloadTraitOrProduct(id: Long, refs: AnalysisRefs) {
        when {
            refs.traits.traitSkills.any { it.id == id } ->
                resourcesDownloadService.downloadTraitSkillImage(id, refs.traits)

            refs.items.items.firstOrNull { it.id == id } != null ->
                resourcesDownloadService.downloadItemImage(refs.items.items.first { it.id == id })

            refs.tacticals.tacticalSkills.firstOrNull { it.id == id } != null ->
                resourcesDownloadService.downloadTacticalSkillImage(refs.tacticals.tacticalSkills.first { it.id == id })
        }
    }

    private fun buildAnalysis(data: Data, refs: AnalysisRefs): CharacterDetail.CharacterAnalysis {
        val snapshot = data.characterDetailStatSnapshot
            ?: throw MessageReplyException("暂无该角色的统计数据")
        val stat = snapshot.characterDetailStat
        val totalGames = snapshot.tierGameCount

        val weapons = stat.weaponStats
            .map { buildWeapon(it, totalGames, refs) }
            .sortedByDescending { it.games }
        val characterTier = weapons.firstOrNull()?.tier ?: ""

        val topPlayers = data.playerTiers
            .map { pt ->
                val tier = refs.tiers.tiers.firstOrNull { it.id == pt.tierId }
                CharacterDetail.TopPlayer(
                    name = data.players.firstOrNull { it.userNum == pt.userNum }?.name ?: "",
                    mmr = pt.mmr,
                    tierName = tier?.name ?: "",
                    tierIconUrl = tier?.let {
                        ImageResourcesType.TierRound.getGeneralPath(it.id.toString())
                    } ?: "",
                )
            }
            .sortedByDescending { it.mmr }
            .take(TOP_PLAYER_LIMIT)

        return CharacterDetail.CharacterAnalysis(
            tier = snapshot.tier,
            tierLabel = TIER_LABELS[snapshot.tier] ?: snapshot.tier,
            matchingModeLabel = MATCHING_MODE_LABELS[snapshot.matchingMode] ?: snapshot.matchingMode.toString(),
            teamModeLabel = TEAM_MODE_LABELS[snapshot.teamMode] ?: snapshot.teamMode.toString(),
            updatedLabel = formatUpdated(data.meta?.updatedAt ?: 0L),
            patchLabel = data.patches.joinToString(" / "),
            totalGames = totalGames,
            characterGames = stat.count,
            pickRate = rate(stat.count, totalGames),
            characterTier = characterTier,
            weapons = weapons,
            topPlayers = topPlayers,
        )
    }

    private fun buildWeapon(
        weapon: WeaponStat,
        totalGames: Long,
        refs: AnalysisRefs,
    ): CharacterDetail.WeaponBuild {
        val count = weapon.count
        val rank = weapon.rank

        val skills = SKILL_SLOTS.mapNotNull { slot ->
            refs.skills.findSkill(refs.characterId, slot)?.let { skill ->
                CharacterDetail.SkillSlot(
                    id = skill.id,
                    slot = skill.slot,
                    name = skill.name,
                    iconUrl = ImageResourcesType.Skill.getGeneralPath(skill.id.toString()),
                )
            }
        }

        val skillBuilds = weapon.skillBuildStats
            .sortedByDescending { it.count }
            .take(SKILL_BUILD_LIMIT)
            .map { build ->
                CharacterDetail.SkillBuild(
                    priority = build.key.map { it.toString() },
                    order = build.orderStats.maxByOrNull { it.count }
                        ?.key?.map { it.toString() } ?: emptyList(),
                    pickRate = rate(build.count, count),
                    winRate = rate(build.win, build.count),
                )
            }

        val itemBuilds = weapon.itemBuildStats
            .sortedByDescending { it.count }
            .take(ITEM_BUILD_LIMIT)
            .map { build ->
                val items = build.key.map { itemId ->
                    val item = refs.items.items.firstOrNull { it.id == itemId }
                    CharacterDetail.Pick(
                        id = itemId,
                        name = item?.name ?: "",
                        iconUrl = item?.let { ImageResourcesType.Item.getGeneralPath(it.id.toString()) } ?: "",
                        bgUrl = item?.let {
                            ImageResourcesType.ItemBg.getGeneralPath(itemGradeNum(it.grade).toString())
                        } ?: "",
                        pickRate = 0.0,
                        winRate = 0.0,
                    )
                }
                val order = build.orderStats.maxByOrNull { it.count }
                    ?.let { reorderItemBuild(items, it.key) } ?: emptyList()
                CharacterDetail.ItemBuild(
                    items = items,
                    order = order,
                    pickRate = rate(build.count, count),
                    winRate = rate(build.win, build.count),
                )
            }

        val topEquipments = buildTopEquipments(weapon.itemBuildStats, count, refs)

        val tacticals = weapon.tacticalSkillStats
            .sortedByDescending { it.count }
            .take(TACTICAL_LIMIT)
            .map { tactical ->
                val skill = refs.tacticals.tacticalSkills.firstOrNull { it.id == tactical.key }
                CharacterDetail.Pick(
                    id = tactical.key,
                    name = skill?.name ?: "",
                    iconUrl = skill?.let {
                        ImageResourcesType.TacticalSkill.getGeneralPath(it.id.toString())
                    } ?: "",
                    bgUrl = "",
                    pickRate = rate(tactical.count, count),
                    winRate = rate(tactical.win, tactical.count),
                )
            }

        val augments = weapon.traitCoreStats
            .sortedByDescending { it.count }
            .take(AUGMENT_CORE_LIMIT)
            .map { core ->
                val subs = core.stats
                    .sortedByDescending { it.count }
                    .take(AUGMENT_SUB_LIMIT)
                    .map { traitPick(it.key, it.count, core.count, it.win, refs) }
                CharacterDetail.Augment(
                    core = traitPick(core.key, core.count, count, core.win, refs),
                    subs = subs,
                )
            }

        val infusions = weapon.infusionStats
            .sortedByDescending { it.count }
            .take(INFUSION_LIMIT)
            .map { infusionPick(it.key, it.count, count, it.win, refs) }

        return CharacterDetail.WeaponBuild(
            weaponId = weapon.key.toLong(),
            weapon = weaponName(refs.weapons, weapon.key),
            iconUrl = refs.weapons.masteries.firstOrNull { it.id == weapon.key }?.let {
                ImageResourcesType.Weapon.getGeneralPath(it.id.toString())
            } ?: "",
            tier = weapon.tier,
            tierScore = weapon.tierScore ?: 0.0,
            games = count,
            pickRate = rate(count, totalGames),
            winRate = rate(weapon.win, count),
            top3Rate = rate(weapon.top3, count),
            avgRank = avg(weapon.place, count),
            avgKills = avg(weapon.playerKill, count),
            rank = rank?.count ?: 0L,
            rankSize = rank?.size ?: 0L,
            skills = skills,
            skillBySlot = skills.associateBy { it.slot },
            skillBuilds = skillBuilds,
            topEquipments = topEquipments,
            itemBuilds = itemBuilds,
            tacticals = tacticals,
            augments = augments,
            infusions = infusions,
        )
    }

    private fun buildTopEquipments(
        itemBuildStats: List<CharacterAnalysisResponse.ItemBuildStat>,
        total: Long,
        refs: AnalysisRefs,
    ): List<CharacterDetail.EquipmentSlotPick> {
        return ITEM_SLOTS.mapIndexedNotNull { slotIndex, slot ->
            val countsByItem = mutableMapOf<Long, Long>()
            itemBuildStats.forEach { build ->
                val itemId = build.key.getOrNull(slotIndex) ?: return@forEach
                countsByItem[itemId] = (countsByItem[itemId] ?: 0L) + build.count
            }

            val (itemId, count) = countsByItem.maxByOrNull { it.value } ?: return@mapIndexedNotNull null
            val item = refs.items.items.firstOrNull { it.id == itemId }
            CharacterDetail.EquipmentSlotPick(
                slot = slot,
                id = itemId,
                name = item?.name ?: "",
                iconUrl = item?.let { ImageResourcesType.Item.getGeneralPath(it.id.toString()) } ?: "",
                bgUrl = item?.let {
                    ImageResourcesType.ItemBg.getGeneralPath(itemGradeNum(it.grade).toString())
                } ?: "",
                pickRate = rate(count, total),
            )
        }
    }

    private fun traitPick(
        id: Long,
        count: Long,
        total: Long,
        win: Long,
        refs: AnalysisRefs,
    ): CharacterDetail.Pick {
        val skill = refs.traits.traitSkills.firstOrNull { it.id == id }
        return CharacterDetail.Pick(
            id = id,
            name = skill?.name ?: "",
            iconUrl = skill?.let { ImageResourcesType.TraitSkill.getGeneralPath(it.id.toString()) } ?: "",
            bgUrl = "",
            pickRate = rate(count, total),
            winRate = rate(win, count),
        )
    }

    private fun infusionPick(
        productId: Long,
        count: Long,
        total: Long,
        win: Long,
        refs: AnalysisRefs,
    ): CharacterDetail.Pick {
        val (name, iconUrl) = resolveInfusionProduct(productId, refs)
        return CharacterDetail.Pick(
            id = productId,
            name = name,
            iconUrl = iconUrl,
            bgUrl = "",
            pickRate = rate(count, total),
            winRate = rate(win, count),
        )
    }

    /** 灌注 productId → (名称, 本地图标路径)：优先强化(trait)，其次物品，其次战术技能。 */
    private fun resolveInfusionProduct(productId: Long, refs: AnalysisRefs): Pair<String, String> {
        refs.traits.traitSkills.firstOrNull { it.id == productId }?.let {
            return it.name to ImageResourcesType.TraitSkill.getGeneralPath(productId.toString())
        }
        refs.items.items.firstOrNull { it.id == productId }?.let {
            return it.name to ImageResourcesType.Item.getGeneralPath(productId.toString())
        }
        refs.tacticals.tacticalSkills.firstOrNull { it.id == productId }?.let {
            return it.name to ImageResourcesType.TacticalSkill.getGeneralPath(productId.toString())
        }
        return "" to ""
    }

    private fun reorderItemBuild(
        items: List<CharacterDetail.Pick>,
        orderKey: String,
    ): List<CharacterDetail.Pick> = orderKey.mapNotNull { it.digitToIntOrNull() }
        .mapNotNull { items.getOrNull(it - 1) }

    private fun weaponName(weapons: DakGGWeaponResponse, id: Int): String =
        weapons.masteries.firstOrNull { it.id == id }?.name ?: "武器 $id"

    private fun characterResultPath(character: DakGGCharactersResponse.DakGGCharacterById): String {
        val skinId = character.skins.firstOrNull()?.id ?: 0L
        return ImageResourcesType.Character.getCharacterPath(
            character.id.toInt(), skinId, DakGGCharacterImgType.CharResult
        )
    }

    private fun rate(numerator: Long, denominator: Long): Double =
        if (denominator > 0) numerator.toDouble() / denominator * 100.0 else 0.0

    private fun avg(numerator: Long, denominator: Long): Double =
        if (denominator > 0) numerator.toDouble() / denominator else 0.0

    private fun itemGradeNum(grade: String): Int = when (grade) {
        "Common" -> 1
        "Uncommon" -> 2
        "Rare" -> 3
        "Epic" -> 4
        "Legend" -> 5
        "Mythic" -> 6
        else -> 0
    }

    private fun formatUpdated(updatedAt: Long): String {
        if (updatedAt <= 0) return "未知"
        val diff = System.currentTimeMillis() - updatedAt * 1000
        return when {
            diff < 60_000 -> "刚刚"
            diff < 3_600_000 -> "${diff / 60_000}分钟前"
            diff < 86_400_000 -> "${diff / 3_600_000}小时前"
            diff < 7 * 86_400_000 -> "${diff / 86_400_000}天前"
            else -> "更久前"
        }
    }

    private data class AnalysisRefs(
        val characterId: Long,
        val analysis: CharacterAnalysisResponse,
        val weapons: DakGGWeaponResponse,
        val tiers: DakGGTiersResponse,
        val items: DakGGItemsResponse,
        val tacticals: DakGGTacticalSkillResponse,
        val traits: DakGGTraitSkillsResponse,
        val skills: DakGGSkillsResponse,
    )
}
