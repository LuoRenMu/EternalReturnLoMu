package cn.luorenmu.service

import cn.luorenmu.common.util.ResourceCheckUtil
import cn.luorenmu.common.util.toPath
import cn.luorenmu.request.api.Api.Companion.ioAsync
import cn.luorenmu.request.api.Api.Companion.ioLaunch
import cn.luorenmu.request.api.entity.module.ImageResourcesType
import cn.luorenmu.request.entity.module.MatchingMode
import cn.luorenmu.request.api.entity.response.dakgg.*
import cn.luorenmu.request.api.entity.response.game.BattleUserGamesResponse.UserGame
import cn.luorenmu.request.api.impl.EternalReturnDakGGApi
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json

/**
 *
 * @author LoMu
 * Date 2025/11/5 12:34
 */
open class ResourcesDownloadService {

    private val log = KotlinLogging.logger {}

    private val infusionJson = Json { ignoreUnknownKeys = true }

    private suspend fun downloadIfAbsent(url: String, type: ImageResourcesType, id: String) {
        val path = type.getGeneralPath(id).toPath()
        if (ResourceCheckUtil.checkResource(path)) return
        coroutineScope {
            ioLaunch { EternalReturnDakGGApi.Image.DakGGImageUrlResources(url, type, id).callStream() }
        }
    }

    suspend fun downloadItemImage(item: DakGGItemsResponse.Item) =
        downloadIfAbsent(item.imageUrl, ImageResourcesType.Item, item.id.toString())

    open suspend fun downloadProfileData(nickname: String) {
        coroutineScope {
            val profileDF = ioAsync {
                EternalReturnDakGGApi.User.GetProfile(nickname).execute()
            }
            val charactersDF = ioAsync {
                EternalReturnDakGGApi.Data.GetCharacters.execute()
            }
            val (profile, charactersResponse) = profileDF.await() to charactersDF.await()
            val characters = profile.playerSeasonOverviews.firstOrNull()?.characterStats
            characters?.forEach { character ->
                ioLaunch {
                    val characterById = charactersResponse.getCharacterById(character.key)
                    val skin = character.skinStats?.firstOrNull()?.key ?: characterById.skins.first().id
                    downloadCharacterImage(characterById, skin)
                }
            }
            profile.playerSeasonOverviews.flatMap { it.duoStats }.flatMap { it.characterStats }
                .distinctBy { it.key }
                .forEach { character ->
                    ioLaunch {
                        val characterById = charactersResponse.getCharacterById(character.key)
                        downloadCharacterImage(characterById, characterById.skins.first().id)
                    }
                }
        }
    }

    /**
     * 使用预取的 profile 下载角色图片，跳过 GetProfile API 调用。
     */
    open suspend fun downloadProfileData(profile: DakGGProfileResponse) {
        coroutineScope {
            val charactersResponse = ioAsync {
                EternalReturnDakGGApi.Data.GetCharacters.execute()
            }.await()
            val characters = profile.playerSeasonOverviews.firstOrNull()?.characterStats
            characters?.forEach { character ->
                ioLaunch {
                    val characterById = charactersResponse.getCharacterById(character.key)
                    val skin = character.skinStats?.firstOrNull()?.key ?: characterById.skins.first().id
                    downloadCharacterImage(characterById, skin)
                }
            }
            profile.playerSeasonOverviews.flatMap { it.duoStats }.flatMap { it.characterStats }
                .distinctBy { it.key }
                .forEach { character ->
                    ioLaunch {
                        val characterById = charactersResponse.getCharacterById(character.key)
                        downloadCharacterImage(characterById, characterById.skins.first().id)
                    }
                }
        }
    }

    suspend fun downloadWeaponImage(weapon: DakGGWeaponResponse.Weapon) =
        downloadIfAbsent(weapon.iconUrl, ImageResourcesType.Weapon, weapon.id.toString())

    suspend fun downloadTacticalSkillImage(tacticalSkill: DakGGTacticalSkillResponse.TacticalSkill) =
        downloadIfAbsent(tacticalSkill.imageUrl, ImageResourcesType.TacticalSkill, tacticalSkill.id.toString())

    suspend fun downloadSkillImage(skill: DakGGSkillsResponse.DakGGSkill) =
        downloadIfAbsent(skill.imageUrl, ImageResourcesType.Skill, skill.id.toString())

    suspend fun downloadTraitSkillImage(traitSkillId: Long, traitSkills: DakGGTraitSkillsResponse) {
        val traitSkill = traitSkills.getTraitSkillById(traitSkillId)
        val traitSkillGroup = traitSkills.traitSkillGroups.firstOrNull { it.key == traitSkill.group }

        val traitSkillPath = ImageResourcesType.TraitSkill.getGeneralPath(traitSkillId.toString()).toPath()
        val groupPath = traitSkillGroup?.let {
            ImageResourcesType.TraitSkillGroup.getGeneralPath(it.key).toPath()
        } ?: ImageResourcesType.TraitSkillGroupPlaceholder.getGeneralPath("").toPath()

        if (ResourceCheckUtil.checkResource(traitSkillPath) && ResourceCheckUtil.checkResource(groupPath)) return

        coroutineScope {
            ioLaunch {
                traitSkillGroup?.let {
                    EternalReturnDakGGApi.Image.DakGGImageUrlResources(
                        it.imageUrl,
                        ImageResourcesType.TraitSkillGroup,
                        it.key
                    ).callStream()
                } ?: run {
                    EternalReturnDakGGApi.Image.DakGGImageUrlResources(
                        ImageResourcesType.TRAIT_SKILL_GROUP_PLACEHOLDER_WILSON_URL,
                        ImageResourcesType.TraitSkillGroupPlaceholder,
                        ""
                    ).callStream()
                }
                EternalReturnDakGGApi.Image.DakGGImageUrlResources(
                    traitSkill.imageUrl,
                    ImageResourcesType.TraitSkill,
                    traitSkillId.toString()
                ).callStream()
            }
        }
    }

    suspend fun downloadCharacterImage(
        character: DakGGCharactersResponse.DakGGCharacterById,
        skinCode: Long,
    ) {
        val firstPath = ImageResourcesType.Character.getCharacterPath(
            character.id.toInt(), skinCode, DakGGCharacterImgType.CharProfile
        ).toPath()
        if (ResourceCheckUtil.checkResource(firstPath)) return

        coroutineScope {
            val characterSkinById = character.getCharacterSkinById(skinCode)
            DakGGCharacterImgType.entries.forEach {
                ioLaunch {
                    EternalReturnDakGGApi.Image.DakGGImageUrlCharacter(
                        characterSkinById.imageUrl,
                        character.id.toInt(),
                        characterSkinById.id,
                        it
                    ).callStream()
                }
            }
        }
    }

    open suspend fun downloadTiers(tiers: DakGGTiersResponse) {
        coroutineScope {
            for (tier in tiers.tiers.distinctBy { it.id }) {
                val tierType = tier.id
                val fullPath = ImageResourcesType.TierFull.getGeneralPath(tierType.toString()).toPath()
                val roundPath = ImageResourcesType.TierRound.getGeneralPath(tierType.toString()).toPath()
                if (!ResourceCheckUtil.checkResource(fullPath)) {
                    ioLaunch {
                        EternalReturnDakGGApi.Image.DakGGImageUrlResources(
                            url = tier.imageUrl.replace("assets/", "").replace("rank", "tier"),
                            ImageResourcesType.TierFull,
                            tierType.toString()
                        ).callStream()
                    }
                }
                if (!ResourceCheckUtil.checkResource(roundPath)) {
                    ioLaunch {
                        EternalReturnDakGGApi.Image.DakGGImageUrlResources(
                            tier.iconUrl,
                            ImageResourcesType.TierRound,
                            tierType.toString()
                        ).callStream()
                    }
                }
            }
        }
    }

    open suspend fun gameDataDownload(games: MutableList<UserGame>) {
        lateinit var characterResponse: DakGGCharactersResponse
        lateinit var weaponResponse: DakGGWeaponResponse
        lateinit var traitSkillResponse: DakGGTraitSkillsResponse
        lateinit var itemsResponse: DakGGItemsResponse
        lateinit var tacticalSkillResponse: DakGGTacticalSkillResponse
        lateinit var tiers: DakGGTiersResponse
        lateinit var infusionsResponse: DakGGInfusionsResponse

        coroutineScope {
            val characterResponseDF = ioAsync { EternalReturnDakGGApi.Data.GetCharacters.execute() }
            val weaponResponseDF = ioAsync { EternalReturnDakGGApi.Data.GetWeapons.execute() }
            val traitSkillResponseDF = ioAsync { EternalReturnDakGGApi.Data.GetTraitSkills.execute() }
            val itemsResponseDF = ioAsync { EternalReturnDakGGApi.Data.GetItems.execute() }
            val tacticalSkillResponseDF = ioAsync { EternalReturnDakGGApi.Data.GetTacticalSkills.execute() }
            val tiersDF = ioAsync { EternalReturnDakGGApi.Data.GetTiers.execute() }
            val infusionsResponseDF = ioAsync { EternalReturnDakGGApi.Data.GetInfusions.execute() }

            characterResponse = characterResponseDF.await()
            weaponResponse = weaponResponseDF.await()
            traitSkillResponse = traitSkillResponseDF.await()
            itemsResponse = itemsResponseDF.await()
            tacticalSkillResponse = tacticalSkillResponseDF.await()
            tiers = tiersDF.await()
            infusionsResponse = infusionsResponseDF.await()
        }

        log.debug { "gameDataDownload 数据已收集完毕" }

        log.debug { "gameDataDownload 开始下载天赋" }
        downloadTiers(tiers)
        log.debug { "gameDataDownload 开始下载角色" }
        for (game in games.distinctBy { "${it.characterNum}${it.skinCode}" }) {
            val characterNum = game.characterNum
            val skinCode = game.skinCode
            downloadCharacterImage(characterResponse.getCharacterById(characterNum), skinCode)
        }

        log.debug { "gameDataDownload 开始下载武器" }
        for (game in games.distinctBy { it.bestWeapon }) {
            downloadWeaponImage(weaponResponse.getWeaponById(game.bestWeapon))
        }

        log.debug { "gameDataDownload 开始下载天赋" }
        for (game in games) {
            val traitSkillId = game.traitFirstCore
            val traitSecondSubId = game.traitSecondSub.firstOrNull()
            downloadTraitSkillImage(traitSkillId, traitSkillResponse)
            traitSecondSubId?.let {
                downloadTraitSkillImage(it, traitSkillResponse)
            }
        }

        log.debug { "gameDataDownload 开始下载钴协议灌注 Trait 图标" }
        val infusionTraitIds = mutableSetOf<Long>()
        for (game in games.filter { MatchingMode.convert(it.matchingMode) == MatchingMode.Cobalt }) {
            collectInfusionTraitIds(game.boughtInfusion, infusionsResponse, infusionTraitIds)
        }
        coroutineScope {
            for (traitId in infusionTraitIds) {
                ioLaunch { downloadTraitSkillImage(traitId, traitSkillResponse) }
            }
        }

        log.debug { "gameDataDownload 开始下载装备" }
        val equipmentIds = games.flatMap { it.equipmentReal.values }.map { it.toLong() }
        for (equipmentId in equipmentIds) {
            downloadItemImage(itemsResponse.getItemById(equipmentId))
        }

        log.debug { "gameDataDownload 开始下载实验体技能" }
        for (game in games.distinctBy { it.tacticalSkillGroup }) {
            downloadTacticalSkillImage(
                tacticalSkillResponse.getTacticalSkill(game.tacticalSkillGroup)
            )
        }

        log.debug { "gameDataDownload 开始下载装备背景图片" }
        for (id in games.flatMap { game -> game.equipmentGradeReal.values }.toList()) {
            downloadItemBgImage(id)
        }
        log.debug { "gameDataDownload 开始下载 banner" }
        downloadBanner(games)
        log.debug { "gameDataDownload 全部已完成" }
    }

    private fun collectInfusionTraitIds(
        raw: String,
        infusions: DakGGInfusionsResponse,
        out: MutableSet<Long>,
    ) {
        if (raw.isBlank()) return
        val map = try {
            infusionJson.decodeFromString<Map<String, Long>>(raw)
        } catch (_: Exception) {
            return
        }
        for ((idStr, _) in map) {
            val id = idStr.toLongOrNull() ?: continue
            val infusion = infusions.getInfusionById(id)
            if (infusion?.productType == "Trait") out.add(infusion.productId)
        }
    }

    /**
     * 与 Search.tsx 一致的 banner 公式：bannerId = floor((seasonId - 1) / 2) * 2 - 27
     * 初次按需下载 dak.gg 的 bg-landing-search-v{bannerId}.jpg 到本地 resources/images/bg/。
     */
    private suspend fun downloadBanner(games: MutableList<UserGame>) {
        val seasonId = games.maxOfOrNull { it.seasonId }?.toInt()?.takeIf { it > 0 } ?: 39
        val bannerId = (seasonId - 1) / 2 * 2 - 27
        val name = "bg-landing-search-v${bannerId}"
        val path = ImageResourcesType.Banner.getGeneralPath(name).toPath()
        if (ResourceCheckUtil.checkResource(path)) return
        coroutineScope {
            ioLaunch {
                EternalReturnDakGGApi.Image.DakGGImageUrlResources(
                    "https://cdn.dak.gg/er/images/bg/${name}.jpg",
                    ImageResourcesType.Banner,
                    name
                ).callStream()
            }
        }
    }

    suspend fun downloadItemBgImage(id: Int) {
        val path = ImageResourcesType.ItemBg.getGeneralPath(id.toString()).toPath()
        if (ResourceCheckUtil.checkResource(path)) return
        coroutineScope {
            ioLaunch {
                EternalReturnDakGGApi.Image.DakGGImageUrlItemBg(
                    id.toString()
                ).callStream()
            }
        }
    }
}
