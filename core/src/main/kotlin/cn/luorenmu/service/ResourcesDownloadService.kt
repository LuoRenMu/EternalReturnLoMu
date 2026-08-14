package cn.luorenmu.service

import cn.luorenmu.common.util.ResourceCheckUtil
import cn.luorenmu.common.util.toPath
import cn.luorenmu.request.api.Api.Companion.ioAsync
import cn.luorenmu.request.api.entity.module.ImageResourcesType
import cn.luorenmu.request.entity.module.MatchingMode
import cn.luorenmu.request.api.entity.response.dakgg.*
import cn.luorenmu.request.api.entity.response.game.BattleUserGamesResponse.UserGame
import cn.luorenmu.request.api.impl.EternalReturnDakGGApi
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json

/**
 *
 * @author LoMu
 * Date 2025/11/5 12:34
 */
open class ResourcesDownloadService {

    private val log = KotlinLogging.logger {}

    private val infusionJson = Json { ignoreUnknownKeys = true }
    private val downloadPermits = Semaphore(MAX_CONCURRENT_DOWNLOADS)

    private suspend fun downloadIfAbsent(url: String, type: ImageResourcesType, id: String) {
        val path = type.getGeneralPath(id).toPath()
        if (ResourceCheckUtil.checkResource(path)) return
        downloadPermits.withPermit {
            EternalReturnDakGGApi.Image.DakGGImageUrlResources(url, type, id).callStream()
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
            downloadProfileCharacters(profile, charactersResponse)
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
            downloadProfileCharacters(profile, charactersResponse)
        }
    }

    private suspend fun downloadProfileCharacters(
        profile: DakGGProfileResponse,
        characters: DakGGCharactersResponse,
    ) {
        downloadAll(profileCharacterImageRequests(profile, characters)) { (character, skinCode) ->
            downloadCharacterImage(character, skinCode)
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
        coroutineScope {
            listOf(
                async {
                    if (traitSkillGroup != null) {
                        downloadIfAbsent(traitSkillGroup.imageUrl, ImageResourcesType.TraitSkillGroup, traitSkillGroup.key)
                    } else {
                        downloadIfAbsent(
                            ImageResourcesType.TRAIT_SKILL_GROUP_PLACEHOLDER_WILSON_URL,
                            ImageResourcesType.TraitSkillGroupPlaceholder,
                            "",
                        )
                    }
                },
                async {
                    downloadIfAbsent(traitSkill.imageUrl, ImageResourcesType.TraitSkill, traitSkillId.toString())
                }
            ).awaitAll()
        }
    }

    suspend fun downloadCharacterImage(
        character: DakGGCharactersResponse.DakGGCharacterById,
        skinCode: Long,
    ) {
        val missingTypes = DakGGCharacterImgType.entries.filter { type ->
            !ResourceCheckUtil.checkResource(
                ImageResourcesType.Character.getCharacterPath(character.id.toInt(), skinCode, type).toPath()
            )
        }
        if (missingTypes.isEmpty()) return

        val characterSkinById = character.getCharacterSkinById(skinCode)
        coroutineScope {
            missingTypes.map { type ->
                async {
                    downloadPermits.withPermit {
                    EternalReturnDakGGApi.Image.DakGGImageUrlCharacter(
                        characterSkinById.imageUrl,
                        character.id.toInt(),
                        characterSkinById.id,
                            type,
                    ).callStream()
                    }
                }
            }.awaitAll()
        }
    }

    open suspend fun downloadTiers(tiers: DakGGTiersResponse) {
        downloadAll(tiers.tiers.distinctBy { it.id }) { tier ->
            coroutineScope {
                listOf(
                    async { downloadIfAbsent(tier.imageUrl.replace("assets/", "").replace("rank", "tier"), ImageResourcesType.TierFull, tier.id.toString()) },
                    async { downloadIfAbsent(tier.iconUrl, ImageResourcesType.TierRound, tier.id.toString()) },
                ).awaitAll()
            }
        }
    }

    open suspend fun gameDataDownload(
        games: List<UserGame>,
        characterResponse: DakGGCharactersResponse? = null,
        tiers: DakGGTiersResponse? = null,
        infusionsResponse: DakGGInfusionsResponse? = null,
    ) {
        lateinit var resolvedCharacters: DakGGCharactersResponse
        lateinit var weaponResponse: DakGGWeaponResponse
        lateinit var traitSkillResponse: DakGGTraitSkillsResponse
        lateinit var itemsResponse: DakGGItemsResponse
        lateinit var tacticalSkillResponse: DakGGTacticalSkillResponse
        lateinit var resolvedTiers: DakGGTiersResponse
        lateinit var resolvedInfusions: DakGGInfusionsResponse

        coroutineScope {
            val characterResponseDF = characterResponse?.let { null } ?: ioAsync { EternalReturnDakGGApi.Data.GetCharacters.execute() }
            val weaponResponseDF = ioAsync { EternalReturnDakGGApi.Data.GetWeapons.execute() }
            val traitSkillResponseDF = ioAsync { EternalReturnDakGGApi.Data.GetTraitSkills.execute() }
            val itemsResponseDF = ioAsync { EternalReturnDakGGApi.Data.GetItems.execute() }
            val tacticalSkillResponseDF = ioAsync { EternalReturnDakGGApi.Data.GetTacticalSkills.execute() }
            val tiersDF = tiers?.let { null } ?: ioAsync { EternalReturnDakGGApi.Data.GetTiers.execute() }
            val infusionsResponseDF = infusionsResponse?.let { null } ?: ioAsync { EternalReturnDakGGApi.Data.GetInfusions.execute() }

            resolvedCharacters = characterResponse ?: checkNotNull(characterResponseDF).await()
            weaponResponse = weaponResponseDF.await()
            traitSkillResponse = traitSkillResponseDF.await()
            itemsResponse = itemsResponseDF.await()
            tacticalSkillResponse = tacticalSkillResponseDF.await()
            resolvedTiers = tiers ?: checkNotNull(tiersDF).await()
            resolvedInfusions = infusionsResponse ?: checkNotNull(infusionsResponseDF).await()
        }

        log.debug { "gameDataDownload 数据已收集完毕" }

        log.debug { "gameDataDownload 开始下载天赋" }
        downloadTiers(resolvedTiers)
        log.debug { "gameDataDownload 开始下载角色" }
        downloadAll(games.distinctBy { it.characterNum to it.skinCode }) { game ->
            val characterNum = game.characterNum
            val skinCode = game.skinCode
            downloadCharacterImage(resolvedCharacters.getCharacterById(characterNum), skinCode)
        }

        log.debug { "gameDataDownload 开始下载武器" }
        downloadAll(games.distinctBy { it.bestWeapon }) { game ->
            downloadWeaponImage(weaponResponse.getWeaponById(game.bestWeapon))
        }

        log.debug { "gameDataDownload 开始下载天赋" }
        val traitSkillIds = games.flatMap { game ->
            listOfNotNull(game.traitFirstCore, game.traitSecondSub.firstOrNull())
        }.distinct()
        downloadAll(traitSkillIds) { downloadTraitSkillImage(it, traitSkillResponse) }

        log.debug { "gameDataDownload 开始下载钴协议灌注 Trait 图标" }
        val infusionTraitIds = mutableSetOf<Long>()
        for (game in games.filter { MatchingMode.convert(it.matchingMode) == MatchingMode.Cobalt }) {
            collectInfusionTraitIds(game.boughtInfusion, resolvedInfusions, infusionTraitIds)
        }
        downloadAll(infusionTraitIds) { downloadTraitSkillImage(it, traitSkillResponse) }

        log.debug { "gameDataDownload 开始下载装备" }
        val equipmentIds = games.flatMap { it.equipmentReal.values }.map { it.toLong() }.distinct()
        downloadAll(equipmentIds) { equipmentId ->
            downloadItemImage(itemsResponse.getItemById(equipmentId))
        }

        log.debug { "gameDataDownload 开始下载实验体技能" }
        downloadAll(games.distinctBy { it.tacticalSkillGroup }) { game ->
            downloadTacticalSkillImage(
                tacticalSkillResponse.getTacticalSkill(game.tacticalSkillGroup)
            )
        }

        log.debug { "gameDataDownload 开始下载装备背景图片" }
        downloadAll(games.flatMap { game -> game.equipmentGradeReal.values }.distinct()) { id ->
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
    private suspend fun downloadBanner(games: List<UserGame>) {
        val seasonId = games.maxOfOrNull { it.seasonId }?.toInt()?.takeIf { it > 0 } ?: 39
        val name = ImageResourcesType.bannerNameForSeason(seasonId)
        val path = ImageResourcesType.bannerPathForSeason(seasonId).toPath()
        if (ResourceCheckUtil.checkResource(path)) return
        downloadIfAbsent(
            "https://cdn.dak.gg/er/images/bg/${name}.jpg",
            ImageResourcesType.Banner,
            name,
        )
    }

    suspend fun downloadItemBgImage(id: Int) {
        val path = ImageResourcesType.ItemBg.getGeneralPath(id.toString()).toPath()
        if (ResourceCheckUtil.checkResource(path)) return
        downloadPermits.withPermit {
            EternalReturnDakGGApi.Image.DakGGImageUrlItemBg(
                id.toString()
            ).callStream()
        }
    }

    private suspend fun <T> downloadAll(values: Iterable<T>, block: suspend (T) -> Unit) = coroutineScope {
        values.map { value -> async { block(value) } }.awaitAll()
    }

    private companion object {
        const val MAX_CONCURRENT_DOWNLOADS = 8
    }
}

internal fun profileCharacterImageRequests(
    profile: DakGGProfileResponse,
    characters: DakGGCharactersResponse,
): List<Pair<DakGGCharactersResponse.DakGGCharacterById, Long>> {
    val overviewCharacters = profile.playerSeasonOverviews
        .flatMap { it.characterStats }
        .map { stat ->
            val character = characters.getCharacterById(stat.key)
            character to (stat.skinStats?.firstOrNull()?.key ?: character.skins.first().id)
        }
    val duoCharacters = profile.playerSeasonOverviews
        .flatMap { it.duoStats }
        .flatMap { it.characterStats }
        .map { stat ->
            val character = characters.getCharacterById(stat.key)
            character to character.skins.first().id
        }
    return (overviewCharacters + duoCharacters).distinctBy { (character, skinCode) -> character.id to skinCode }
}
