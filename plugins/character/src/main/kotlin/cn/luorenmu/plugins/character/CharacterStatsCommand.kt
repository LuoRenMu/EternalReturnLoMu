package cn.luorenmu.plugins.character

import cn.luorenmu.Adapter
import cn.luorenmu.command.CommandEvent
import cn.luorenmu.command.entity.CommandOptional
import cn.luorenmu.command.entity.MessageSender
import cn.luorenmu.common.annotation.BotCommand
import cn.luorenmu.common.extensions.toPinYin
import cn.luorenmu.common.util.PathUtils
import cn.luorenmu.nutdraw.NutDraw
import cn.luorenmu.request.api.Api.Companion.ioLaunch
import cn.luorenmu.request.api.impl.EternalReturnDakGGApi
import cn.luorenmu.request.entity.module.DakGGRank
import cn.luorenmu.request.entity.module.DakGGTeamMode
import cn.luorenmu.request.entity.module.MatchingMode
import cn.luorenmu.service.ResourcesDownloadService
import kotlinx.coroutines.coroutineScope
import love.forte.simbot.message.Message
import love.forte.simbot.message.OfflineImage
import love.forte.simbot.message.toText
import org.koin.java.KoinJavaComponent.inject

/**
 * @author LoMu
 * Date 2026/5/24
 */
@BotCommand("角色数据统计", "角色数据", "<tier> <rank> <mode>", adapter = [Adapter.QG_BOT, Adapter.ONE_BOT])
class CharacterStatsCommand : CommandEvent {
    override val example: String = "/角色数据 s 铁阎 排位"
    override val optionals: List<CommandOptional> =
        listOf(
            CommandOptional(
                name = "tierOrCharacter",
                description = "查询评级(s、a、b、c、d)或指定角色(支持拼音)",
                required = false
            ),
            CommandOptional(
                name = "rank",
                description = "查询段位(铁阎、灭钻、in1000等)",
                required = false
            ),
            CommandOptional(
                name = "mode",
                description = "查询模式(钴协议、排位)",
                required = false
            )
        )
    override val description =
        "角色数据统计评分排行(因全部图片过大而作筛选 默认s) 如果要直接查询指定段位 需要传递前者参数"

    private val characterStatsCollector = CharacterStatsCollector()
    private val resourcesDownloadService: ResourcesDownloadService by inject(ResourcesDownloadService::class.java)

    private val tiers = listOf("s", "a", "b", "c", "d")
    override suspend fun listen(sender: MessageSender, command: Map<String, String>): Message {
        val mode = when (MatchingMode.convert(command["mode"] ?: "排位")) {
            MatchingMode.Cobalt -> MatchingMode.Cobalt
            MatchingMode.Rank -> MatchingMode.Rank
            else -> MatchingMode.Rank
        }
        var rankStr = command["rank"] ?: "灭钻"
        if (rankStr !in DakGGRank.entries.map { it.shortName }) {
            rankStr = "灭钻"
        }
        val rank = DakGGRank.convert(rankStr)
        var tier = command["tier"] ?: "s"
        val characters = EternalReturnDakGGApi.Data.GetCharacters.execute()
        tier = if (tier !in tiers) {
            characters.characters.firstOrNull { it.name.toPinYin().trim().equals(tier.toPinYin().trim(), true) }?.id?.toString()
                ?: return "不存在的角色名称".toText()
        } else {
            tier
        }
        preheatRequest()
        val stats = characterStatsCollector.collect(
            teamMode = DakGGTeamMode.Squad,
            matchingMode = mode,
            rank = rank,
            tierOrCharacter = tier
        )

        val outputPath = PathUtils.resourcesPathResolve("render", "character_stats_${tier}_${mode}_${rank}.png")
        NutDraw.render(CharacterStatsTemplate(), stats, outputPath)
        return OfflineImage.fileOfflineImage(outputPath.toString())
    }

    private suspend fun preheatRequest() {
        coroutineScope {
            ioLaunch {
                val characters = EternalReturnDakGGApi.Data.GetCharacters.execute()
                characters.characters.forEach { character ->
                    val skinId = character.skins.firstOrNull()?.id ?: return@forEach
                    resourcesDownloadService.downloadCharacterImage(character, skinId)
                }
            }
            ioLaunch {
                val weapons = EternalReturnDakGGApi.Data.GetWeapons.execute()
                weapons.masteries.forEach { weapon ->
                    resourcesDownloadService.downloadWeaponImage(weapon)
                }
            }
        }
    }
}
