package cn.luorenmu.plugins.character

import cn.luorenmu.Adapter
import cn.luorenmu.command.CommandEvent
import cn.luorenmu.command.entity.CommandOptional
import cn.luorenmu.command.entity.MessageSender
import cn.luorenmu.common.annotation.BotCommand
import cn.luorenmu.common.util.PathUtils
import cn.luorenmu.nutdraw.NutDraw
import cn.luorenmu.request.api.Api.Companion.ioLaunch
import cn.luorenmu.request.api.impl.EternalReturnDakGGApi
import cn.luorenmu.request.entity.module.DakGGRank
import cn.luorenmu.service.ResourcesDownloadService
import kotlinx.coroutines.coroutineScope
import love.forte.simbot.message.Message
import love.forte.simbot.message.OfflineImage
import org.koin.java.KoinJavaComponent.inject

/**
 * @author LoMu
 * Date 2026/5/24
 */
@BotCommand("角色数据统计", "角色数据", "<rank>", adapter = [Adapter.QG_BOT, Adapter.ONE_BOT])
class CharacterStatsCommand : CommandEvent {
    override val example: String = "/角色数据 灭钻"
    override val optionals: List<CommandOptional> =
        listOf(
            CommandOptional(
                name = "rank",
                description = "查询段位（铁阎、青铜、白银、黄金、修罗、灭钻、星陨、无暇、in1000，默认灭钻）",
                required = false,
            )
        )
    override val description = "展示指定段位的全部英雄及武器统计"

    private val characterStatsCollector = CharacterStatsCollector()
    private val resourcesDownloadService: ResourcesDownloadService by inject(ResourcesDownloadService::class.java)

    override suspend fun listen(sender: MessageSender, command: Map<String, String>): Message {
        var rankStr = command["rank"] ?: "灭钻"
        if (rankStr !in DakGGRank.entries.map { it.shortName }) {
            rankStr = "灭钻"
        }
        val rank = DakGGRank.convert(rankStr)
        preheatRequest()
        val stats = characterStatsCollector.collect(rank)

        val outputPath = PathUtils.resourcesPathResolve("render", "character_stats_${rank.value}.png")
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
