package cn.luorenmu.plugins.match

import cn.luorenmu.request.api.entity.module.ImageResourcesType
import cn.luorenmu.request.api.entity.response.dakgg.DakGGCharacterImgType
import cn.luorenmu.request.api.entity.response.dakgg.DakGGCharactersResponse
import cn.luorenmu.request.api.entity.response.dakgg.DakGGTiersResponse
import cn.luorenmu.request.api.impl.EternalReturnDakGGApi
import cn.luorenmu.service.ResourcesDownloadService
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Only download icons used by this page, not player banners, unused tiers or four portrait variants. */
internal object MatchDetailAssets {
    private val log = KotlinLogging.logger {}
    private val permits = Semaphore(6)

    suspend fun download(snapshot: MatchSnapshot, characters: DakGGCharactersResponse, tiers: DakGGTiersResponse,
                         resources: ResourcesDownloadService) = coroutineScope {
        val weaponJob = async { EternalReturnDakGGApi.Data.GetWeapons.execute() }
        val itemJob = async { EternalReturnDakGGApi.Data.GetItems.execute() }
        val traitJob = async { EternalReturnDakGGApi.Data.GetTraitSkills.execute() }
        val tacticalJob = async { EternalReturnDakGGApi.Data.GetTacticalSkills.execute() }
        val weapons = weaponJob.await()
        val items = itemJob.await()
        val traits = traitJob.await()
        val tactical = tacticalJob.await()
        val games = snapshot.games
        val jobs = mutableListOf<suspend () -> Unit>()

        games.distinctBy { it.characterNum to it.skinCode }.forEach { game ->
            val character = characters.characters.firstOrNull { it.id == game.characterNum }
            val skin = character?.skins?.firstOrNull { it.id == game.skinCode }
            if (skin != null) jobs += { EternalReturnDakGGApi.Image.DakGGImageUrlCharacter(
                skin.imageUrl, game.characterNum.toInt(), game.skinCode, DakGGCharacterImgType.CharProfile).callStream() }
        }
        games.map { it.bestWeapon }.filter { it > 0 }.distinct().forEach { id ->
            weapons.masteries.firstOrNull { it.id == id }?.let { weapon -> jobs += { resources.downloadWeaponImage(weapon) } }
        }
        games.flatMap { it.equipmentReal.values }.filter { it > 0 }.distinct().forEach { id ->
            items.items.firstOrNull { it.id == id.toLong() }?.let { item -> jobs += { resources.downloadItemImage(item) } }
        }
        games.map { it.traitFirstCore }.filter { it > 0 }.distinct().forEach { id ->
            traits.traitSkills.firstOrNull { it.id == id }?.let { trait -> jobs += {
                download(trait.imageUrl, ImageResourcesType.TraitSkill, id.toString())
            } }
        }
        games.map { it.tacticalSkillGroup }.filter { it > 0 }.distinct().forEach { id ->
            tactical.tacticalSkills.firstOrNull { it.id == id }?.let { skill -> jobs += { resources.downloadTacticalSkillImage(skill) } }
        }
        games.flatMap { it.equipmentGradeReal.values }.filter { it > 0 }.distinct().forEach { id ->
            jobs += { resources.downloadItemBgImage(id) }
        }
        snapshot.tiers.values.map { it.tierId }.distinct().forEach { id ->
            tiers.tiers.firstOrNull { it.id == id }?.let { tier -> jobs += {
                download(tier.iconUrl, ImageResourcesType.TierRound, id.toString())
            } }
        }
        jobs.map { job -> async {
            permits.withPermit {
                try { job() }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { log.warn(e) { "对局详情图标下载失败，保留结算数据并使用占位图" } }
            }
        } }.awaitAll()
    }

    private suspend fun download(url: String, type: ImageResourcesType, id: String) {
        EternalReturnDakGGApi.Image.DakGGImageUrlResources(url, type, id).callStream()
    }
}
