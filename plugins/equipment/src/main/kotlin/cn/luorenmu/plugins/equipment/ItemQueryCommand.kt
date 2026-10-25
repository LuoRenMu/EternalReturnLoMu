package cn.luorenmu.plugins.equipment

import cn.luorenmu.Adapter
import cn.luorenmu.command.CommandEvent
import cn.luorenmu.command.entity.CommandOptional
import cn.luorenmu.command.entity.MessageSender
import cn.luorenmu.common.annotation.BotCommand
import cn.luorenmu.common.extensions.toPinYin
import cn.luorenmu.common.util.PathUtils
import cn.luorenmu.common.util.RenderedFileCache
import cn.luorenmu.nutdraw.NutDraw
import cn.luorenmu.request.api.entity.response.dakgg.DakGGItemsResponse.Item
import cn.luorenmu.request.api.impl.EternalReturnDakGGApi
import kotlinx.coroutines.CancellationException
import love.forte.simbot.message.Message
import love.forte.simbot.message.OfflineImage
import love.forte.simbot.message.toText

@BotCommand("查询装备", "查询装备", "<item>", adapter = [Adapter.QG_BOT, Adapter.ONE_BOT])
class ItemQueryCommand : CommandEvent {
    override val example = "/查询装备 军刀"
    override val description = "查询装备品质、属性、合成材料和高使用率角色"
    override val optionals = listOf(CommandOptional("item", "装备名/拼音/ID", required = true))

    override suspend fun listen(sender: MessageSender, command: Map<String, String>): Message {
        val query = command["item"]?.trim().orEmpty()
        if (query.isEmpty()) return "请指定装备名、拼音或装备ID，例如 /查询装备 军刀".toText()
        val items = EternalReturnDakGGApi.Data.GetItems.execute().items
        val matches = findEquipment(items, query)
        if (matches.isEmpty()) return "未找到装备：$query，请检查名称或使用装备ID。".toText()
        if (matches.size > 1) return ("找到多个装备，请使用完整名称或ID：\n" +
            matches.take(15).joinToString("\n") { "${it.name} (${it.id})" } +
            if (matches.size > 15) "\n共 ${matches.size} 件，请缩小关键词范围。" else "").toText()
        val item = matches.single()
        val recipe = buildItemRecipeTree(item, items)
        val (characters, updatedAt) = try {
            val stats = EquipmentStatsClient.fetch()
            val characterData = EternalReturnDakGGApi.Data.GetCharacters.execute().characters
            val weaponData = EternalReturnDakGGApi.Data.GetWeapons.execute().masteries
            val ranked = topItemCharacters(item.id, stats, characterData, weaponData)
            val withPortraits = localizeItemPortraits(ranked, characterData)
            withPortraits to stats.meta.updatedAt
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emptyList<ItemCharacterUsage>() to (System.currentTimeMillis() / 3_600_000)
        }
        val key = RenderedFileCache.cacheKey("v4", item.id.toString(), item.tooltip,
            recipe.toString(), updatedAt.toString(), characters.joinToString())
        val path = RenderedFileCache.getOrCreate(PathUtils.resourcesPathResolve("render", "item_$key.png")) {
            NutDraw.render(ItemQueryTemplate(), ItemQueryData(item, recipe, characters), it)
        }
        return OfflineImage.fileOfflineImage(path.toString())
    }
}

internal fun findEquipment(items: List<Item>, query: String): List<Item> {
    val key = query.trim()
    if (key.isEmpty()) return emptyList()
    val equipment = items.filter { it.type == "Weapon" || it.type == "Armor" }
    val exact = equipment.filter { it.id.toString() == key || it.name.equals(key, true) }
    if (exact.isNotEmpty()) return exact
    val pinyin = key.toPinYin()
    val phonetic = equipment.filter { it.name.toPinYin().equals(pinyin, true) }
    return phonetic.ifEmpty { equipment.filter { it.name.contains(key, true) || it.name.toPinYin().contains(pinyin, true) } }
}

/** DAK.GG's item helper switches on grade and defaults to Common (01), not 00. */
internal fun equipmentGradeNumber(grade: String): Int = when (grade) {
    "Uncommon" -> 2
    "Rare" -> 3
    "Epic" -> 4
    "Legend" -> 5
    "Mythic" -> 6
    else -> 1
}

internal fun equipmentBackground(grade: String) =
    "https://cdn.dak.gg/er/images/item/ico-itemgradebg-0${equipmentGradeNumber(grade)}.svg"
