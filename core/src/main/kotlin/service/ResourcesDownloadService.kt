package cn.luorenmu.service

import cn.luorenmu.request.api.EternalReturnDakGGApi
import cn.luorenmu.request.api.EternalReturnDakGGApiClient
import cn.luorenmu.request.api.entity.module.ImageResourcesType
import cn.luorenmu.request.api.entity.response.dakgg.*
import cn.luorenmu.request.api.entity.response.game.BattleUserGamesResponse.UserGame
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject

/**
 *
 * @author LoMu
 * Date 2025/11/5 12:34
 */
class ResourcesDownloadService {

    private val log = KotlinLogging.logger {}
    private val executors: ExecutorCoroutineDispatcher by inject(
        ExecutorCoroutineDispatcher::class.java
    )

    /**
     * 物品、装备
     */
    suspend fun downloadItemImage(item: DakGGItemsResponse.Item) {
        coroutineScope {
            launch(executors) {
                EternalReturnDakGGApi.Image.DakGGImageUrlResources(
                    item.imageUrl,
                    ImageResourcesType.Item,
                    item.id.toString()
                ).callStream()
            }
        }
    }

    suspend fun downloadProfileData(nickname: String) {
        coroutineScope {
            val profileDF = async(executors) {
                EternalReturnDakGGApiClient.getProfile(nickname)
            }
            val charactersDF = async(executors) {
                EternalReturnDakGGApiClient.getCharacters()
            }
            val (profile, charactersResponse) = profileDF.await() to charactersDF.await()
            val characters = profile.playerSeasonOverviews.firstOrNull()?.characterStats
            characters?.forEach { character ->
                launch(executors) {
                    val characterById = charactersResponse.getCharacterById(character.key)
                    /**
                     * 不存在皮肤则获取第一个，第一个为原皮
                     */
                    val skin = character.skinStats?.firstOrNull()?.key ?: characterById.skins.first().id
                    downloadCharacterImage(characterById, skin)
                }
            }
        }


    }

    /**
     * 武器
     */
    suspend fun downloadWeaponImage(weapon: DakGGWeaponResponse.Weapon) {
        coroutineScope {
            launch(executors) {
                EternalReturnDakGGApi.Image.DakGGImageUrlResources(
                    weapon.iconUrl,
                    ImageResourcesType.Weapon,
                    weapon.id.toString()
                ).callStream()
            }
        }
    }

    /**
     *  召唤师技能
     */
    suspend fun downloadTacticalSkillImage(tacticalSkill: DakGGTacticalSkillResponse.TacticalSkill) {
        coroutineScope {
            launch(executors) {
                EternalReturnDakGGApi.Image.DakGGImageUrlResources(
                    tacticalSkill.imageUrl,
                    ImageResourcesType.TacticalSkill,
                    tacticalSkill.id.toString()
                ).callStream()
            }
        }
    }

    /**
     * 天赋技能
     */
    suspend fun downloadTraitSkillImage(traitSkillId: Long, traitSkills: DakGGTraitSkillsResponse) {
        coroutineScope {
            launch(executors) {
                val traitSkill = traitSkills.traitSkills.first { it.id == traitSkillId }
                val traitSkillGroup = traitSkills.traitSkillGroups.firstOrNull { it.key == traitSkill.group }
                traitSkillGroup?.let {
                    EternalReturnDakGGApi.Image.DakGGImageUrlResources(
                        traitSkillGroup.imageUrl,
                        ImageResourcesType.TraitSkillGroup,
                        traitSkillGroup.key
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

        coroutineScope {
            val characterSkinById = character.getCharacterSkinById(skinCode)
            DakGGCharacterImgType.entries.forEach {
                launch(executors) {
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

    suspend fun downloadTiers(tiers: DakGGTiersResponse) {
        coroutineScope {
            for (tier in tiers.tiers.distinctBy { it.id }) {
                val tierType = tier.id
                launch(executors) {
                    EternalReturnDakGGApi.Image.DakGGImageUrlResources(
                        url = tier.imageUrl.replace("assets/", "").replace("rank", "tier"),
                        ImageResourcesType.TierFull,
                        tierType.toString()
                    ).callStream()
                }
                launch(executors) {
                    EternalReturnDakGGApi.Image.DakGGImageUrlResources(
                        tier.iconUrl,
                        ImageResourcesType.TierRound,
                        tierType.toString()
                    ).callStream()
                }
            }
        }
    }

    suspend fun gameDataDownload(games: MutableList<UserGame>) {
        val characterResponse = EternalReturnDakGGApiClient.getCharacters()
        val weaponResponse = EternalReturnDakGGApiClient.getWeapons()
        val traitSkillResponse = EternalReturnDakGGApiClient.getTraitSkills()
        val itemsResponse = EternalReturnDakGGApiClient.getItems()
        val tacticalSkillResponse = EternalReturnDakGGApiClient.getTacticalSkills()
        val tiers = EternalReturnDakGGApiClient.getTiers()
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
        for (game in games.distinctBy { it.traitFirstCore }) {
            val traitSkillId = game.traitFirstCore
            val traitSecondSubId = game.traitSecondSub.firstOrNull()
            downloadTraitSkillImage(traitSkillId, traitSkillResponse)
            traitSecondSubId?.let {
                downloadTraitSkillImage(it, traitSkillResponse)
            }
        }

        log.debug { "gameDataDownload 开始下载装备" }
        val equipmentIds = games.map { it.equipment.values }.flatten().map { it.toLong() }
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

        for (id in games.map { game -> game.equipmentGrade.values }.flatten().toList()) {
            downloadItemBgImage(id)
        }
        log.debug { "gameDataDownload 全部已完成" }
    }

    suspend fun downloadItemBgImage(id: Int) {
        coroutineScope {
            launch(executors) {
                EternalReturnDakGGApi.Image.DakGGImageUrlItemBg(
                    id.toString()
                ).callStream()
            }
        }
    }
}