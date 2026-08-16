package cn.luorenmu.plugins.character

import cn.luorenmu.Adapter
import cn.luorenmu.command.CommandEvent
import cn.luorenmu.command.entity.CommandOptional
import cn.luorenmu.command.entity.MessageSender
import cn.luorenmu.common.annotation.BotCommand
import cn.luorenmu.common.util.PathUtils
import cn.luorenmu.common.util.RenderedFileCache
import cn.luorenmu.nutdraw.NutDraw
import cn.luorenmu.request.entity.module.DakGGRank
import io.github.oshai.kotlinlogging.KotlinLogging
import love.forte.simbot.message.Message
import love.forte.simbot.message.OfflineImage
import love.forte.simbot.message.toText

/**
 * 角色详情分析命令。
 *
 * 展示该角色最热门武器流派的分析：技能加点、推荐出装、战术技能、潜能、灌注与高分玩家。
 *
 * @author LoMu
 * Date 2026/8/8
 */
@BotCommand("查询角色", "查询角色", "<character> <mode> <tier>", adapter = [Adapter.QG_BOT, Adapter.ONE_BOT])
class CharacterDetailCommand : CommandEvent {
    override val example: String = "/查询角色 阿德拉 排位 灭钻"
    override val optionals: List<CommandOptional> =
        listOf(
            CommandOptional(name = "character", description = "角色名或角色ID(支持中文/拼音)", required = true),
            CommandOptional(name = "mode", description = "查询模式 排位/钴协议(默认排位)", required = false),
            CommandOptional(name = "tier", description = "查询段位(仅排位生效 灭钻/无暇/星陨/修罗等 默认灭钻)", required = false),
        )
    override val description = "查询角色详情分析(武器流派/技能加点/出装/潜能)"

    private val log = KotlinLogging.logger {}
    private val characterDetailCollector = CharacterDetailCollector()

    override suspend fun listen(sender: MessageSender, command: Map<String, String>): Message {
        val query = command["character"]?.trim() ?: return "请指定角色名(支持中文/拼音)或角色ID".toText()

        val mode = when (command["mode"]?.trim()) {
            "钴协议" -> "COBALT" to "COBALT"
            else -> "SQUAD" to "RANK"
        }
        val tierKey = if (mode.second == "RANK") {
            DakGGRank.convert(command["tier"]?.trim() ?: "灭钻").value
        } else {
            null
        }

        val cacheKey = RenderedFileCache.cacheKey(
            CACHE_VERSION,
            query.lowercase(),
            mode.first,
            mode.second,
            tierKey.orEmpty(),
        )
        val outputPath = PathUtils.resourcesPathResolve("render", "character_detail_$cacheKey.png")
        val cachedPath = RenderedFileCache.getOrCreate(outputPath) { path ->
            val detail = characterDetailCollector.collect(
                characterQuery = query,
                teamMode = mode.first,
                matchingMode = mode.second,
                tier = tierKey,
            )
            NutDraw.render(CharacterDetailTemplate(), detail, path)
        }
        return OfflineImage.fileOfflineImage(cachedPath.toString())
    }

    private companion object {
        const val CACHE_VERSION = "v1"
    }
}
