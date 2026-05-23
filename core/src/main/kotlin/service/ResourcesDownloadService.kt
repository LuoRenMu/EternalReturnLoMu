package cn.luorenmu.service

import cn.luorenmu.request.api.Api.Companion.ioAsync
import cn.luorenmu.request.api.Api.Companion.ioLaunch
import cn.luorenmu.request.api.entity.module.ImageResourcesType
import cn.luorenmu.request.api.entity.response.dakgg.*
import cn.luorenmu.request.api.entity.response.game.BattleUserGamesResponse.UserGame
import cn.luorenmu.request.api.impl.EternalReturnDakGGApi
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.coroutineScope

/**
 *
 * @author LoMu
 * Date 2025/11/5 12:34
 */
class ResourcesDownloadService {

    private val log = KotlinLogging.logger {}

    /**
     * 物品、装备
     */
    suspend fun downloadItemImage(item: DakGGItemsResponse.Item) {
        coroutineScope {
            ioLaunch {
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
            ioLaunch {
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
            ioLaunch {
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
            ioLaunch {
                val traitSkill = traitSkills.getTraitSkillById(traitSkillId)
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

    suspend fun downloadTiers(tiers: DakGGTiersResponse) {
        coroutineScope {
            for (tier in tiers.tiers.distinctBy { it.id }) {
                val tierType = tier.id
                ioLaunch {
                    // 替换为高分辨率
                    EternalReturnDakGGApi.Image.DakGGImageUrlResources(
                        url = tier.imageUrl.replace("assets/", "").replace("rank", "tier"),
                        ImageResourcesType.TierFull,
                        tierType.toString()
                    ).callStream()
                }
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

    suspend fun gameDataDownload(games: MutableList<UserGame>) {
        lateinit var characterResponse: DakGGCharactersResponse
        lateinit var weaponResponse: DakGGWeaponResponse
        lateinit var traitSkillResponse: DakGGTraitSkillsResponse
        lateinit var itemsResponse: DakGGItemsResponse
        lateinit var tacticalSkillResponse: DakGGTacticalSkillResponse
        lateinit var tiers: DakGGTiersResponse

        coroutineScope {
            val characterResponseDF = ioAsync { EternalReturnDakGGApi.Data.GetCharacters.execute() }
            val weaponResponseDF = ioAsync { EternalReturnDakGGApi.Data.GetWeapons.execute() }
            val traitSkillResponseDF = ioAsync { EternalReturnDakGGApi.Data.GetTraitSkills.execute() }
            val itemsResponseDF = ioAsync { EternalReturnDakGGApi.Data.GetItems.execute() }
            val tacticalSkillResponseDF = ioAsync { EternalReturnDakGGApi.Data.GetTacticalSkills.execute() }
            val tiersDF = ioAsync { EternalReturnDakGGApi.Data.GetTiers.execute() }

            characterResponse = characterResponseDF.await()
            weaponResponse = weaponResponseDF.await()
            traitSkillResponse = traitSkillResponseDF.await()
            itemsResponse = itemsResponseDF.await()
            tacticalSkillResponse = tacticalSkillResponseDF.await()
            tiers = tiersDF.await()
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
                downloadTraitSkillImage(traitSecondSubId, traitSkillResponse)
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
        log.debug { "gameDataDownload 全部已完成" }
    }

    suspend fun downloadItemBgImage(id: Int) {
        coroutineScope {
            ioLaunch {
                EternalReturnDakGGApi.Image.DakGGImageUrlItemBg(
                    id.toString()
                ).callStream()
            }
        }
    }
}