package cn.luorenmu.plugins.equipment

import cn.luorenmu.nutdraw.css.*
import cn.luorenmu.nutdraw.dom.document
import cn.luorenmu.nutdraw.template.ImageTemplate
import cn.luorenmu.nutdraw.template.TemplateDocument
import cn.luorenmu.request.api.entity.response.dakgg.DakGGItemsResponse.Item
import org.jetbrains.skia.Color
import java.util.Locale

data class ItemQueryData(
    val item: Item,
    val recipe: ItemRecipeNode,
    val characters: List<ItemCharacterUsage> = emptyList(),
)

internal fun usagePercent(value: Double): String = String.format(Locale.ROOT, "%.1f%%", value)

internal fun equipmentDescription(tooltip: String): List<String> = tooltip
    .replace(Regex("(?i)<br\\s*/?>"), "\n")
    .replace(Regex("<[^>]*>"), "")
    .replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">")
    .replace("&amp;", "&").replace("&quot;", "\"")
    .lines().flatMap { it.trim().chunked(38).ifEmpty { listOf("") } }

class ItemQueryTemplate : ImageTemplate<ItemQueryData> {
    override fun build(data: ItemQueryData): TemplateDocument {
        val item = data.item
        val lines = equipmentDescription(item.tooltip.ifBlank { "暂无属性说明" })
        val recipeNodes = layoutItemRecipeTree(data.recipe)
        val recipeDepth = recipeNodes.maxOf { it.depth }
        val recipeWidth = maxOf(936, 40 + recipeDepth * 190 + 170)
        val recipeLeft = (recipeWidth - (40 + recipeDepth * 190 + 170)) / 2f + 20f
        val width = recipeWidth + 64
        val recipeHeight = if (data.recipe.children.isEmpty()) 40 else
            recipeNodes.filter { it.node.children.isEmpty() }.size * 96
        val characterRows = data.characters.chunked(2)
        val charactersHeight = 56 + if (characterRows.isEmpty()) 44 else characterRows.size * 102
        val height = 64 + 152 + 52 + lines.size * 32 + 60 + recipeHeight + charactersHeight + 40
        val ink = Color.makeRGB(24, 29, 36)
        val blue = Color.makeRGB(35, 78, 181)
        fun type(size: Int, height: Int, color: Int = ink) = CssStyle(
            width = percent(100), height = px(height), fontSize = size.toFloat(), color = color,
            verticalAlign = VerticalAlign.CENTER)
        return TemplateDocument(width, height, document(CssStyle(
            width = px(width), height = px(height), padding = Edges(32f), background = Color.WHITE,
        ), id = "item-query") {
            element(CssStyle(direction = FlexDirection.ROW, width = percent(100), height = px(152), gap = 24f, alignItems = AlignItems.CENTER)) {
                element(CssStyle(width = px(153), height = px(96), backgroundImage = equipmentBackground(item.grade)), id = "item-grade-background") {
                    image(item.imageUrl, CssStyle(width = percent(100), height = percent(100), objectFit = ObjectFit.CONTAIN))
                }
                element(CssStyle(width = CssSize.Auto, flexGrow = 1f, height = px(120))) {
                    text(item.name, type(34, 64).copy(fontWeight = 800), id = "item-name")
                    val grade = listOf("一般", "高级", "稀有", "英雄", "传说", "超凡")[equipmentGradeNumber(item.grade) - 1]
                    text("$grade · ${if (item.type == "Weapon") "武器" else "防具"} · ID ${item.id}", type(20, 40, blue))
                }
            }
            text("装备属性与说明", type(26, 52, blue).copy(fontWeight = 700))
            lines.forEach { text(it, type(22, 32)) }
            text("装备合成树", type(26, 60, blue).copy(fontWeight = 700))
            if (data.recipe.children.isEmpty()) text("无合成配方（基础装备或特殊来源）", type(22, 40))
            else element(CssStyle(width = px(recipeWidth), height = px(recipeHeight)), id = "recipe-tree") {
                val line = Color.makeRGB(150, 168, 192)
                recipeNodes.filter { it.parentIndex != null }.forEach { child ->
                    val parent = recipeNodes[child.parentIndex!!]
                    val fromX = recipeLeft + parent.depth * 190f + 170f
                    val toX = recipeLeft + child.depth * 190f
                    val middleX = (fromX + toX) / 2f
                    val topY = minOf(parent.centerY, child.centerY)
                    element(CssStyle(position = Position.ABSOLUTE, left = fromX, top = parent.centerY - 1f,
                        width = px(middleX - fromX), height = px(2), background = line))
                    element(CssStyle(position = Position.ABSOLUTE, left = middleX - 1f, top = topY,
                        width = px(2), height = px(kotlin.math.abs(child.centerY - parent.centerY)), background = line))
                    element(CssStyle(position = Position.ABSOLUTE, left = middleX, top = child.centerY - 1f,
                        width = px(toX - middleX), height = px(2), background = line))
                }
                recipeNodes.forEach { placement ->
                    val material = placement.node.item
                    val id = placement.index
                    element(CssStyle(position = Position.ABSOLUTE, left = recipeLeft + placement.depth * 190f,
                        top = placement.centerY - 36f, width = px(170), height = px(72),
                        direction = FlexDirection.ROW, gap = 8f, padding = Edges(8f), alignItems = AlignItems.CENTER,
                        background = Color.makeRGB(240, 243, 248), borderRadius = 8f), id = "recipe-node-$id") {
                        element(CssStyle(width = px(52), height = px(52),
                            backgroundImage = equipmentBackground(material.grade)), id = "material-background-$id") {
                            if (material.imageUrl.isNotBlank()) image(material.imageUrl,
                                CssStyle(width = percent(100), height = percent(100), objectFit = ObjectFit.CONTAIN),
                                id = "material-image-$id")
                        }
                        text(material.name, type(17, 52).copy(width = px(94), fontWeight = 600),
                            id = "material-name-$id")
                    }
                }
            }
            text("高使用率角色  ·  近 7 天三排 / 灭钻及以上", type(26, 56, blue).copy(fontWeight = 700))
            if (characterRows.isEmpty()) text("暂无此装备的角色使用统计", type(22, 44))
            characterRows.forEachIndexed { rowIndex, row ->
                element(CssStyle(direction = FlexDirection.ROW, width = percent(100), height = px(102), gap = 16f)) {
                    row.forEachIndexed { columnIndex, character ->
                        val index = rowIndex * 2 + columnIndex
                        element(CssStyle(direction = FlexDirection.ROW, width = px(460), height = px(92),
                            gap = 12f, padding = Edges(12f), alignItems = AlignItems.CENTER,
                            background = Color.makeRGB(240, 243, 248), borderRadius = 8f), id = "character-$index") {
                            if (character.imageUrl.isNotBlank()) {
                                image(character.imageUrl, CssStyle(width = px(60), height = px(60),
                                    objectFit = ObjectFit.COVER, borderRadius = 6f), id = "character-image-$index")
                            }
                            element(CssStyle(width = CssSize.Auto, flexGrow = 1f, height = px(68))) {
                                text(character.characterName, type(22, 36).copy(fontWeight = 700), id = "character-name-$index")
                                text(character.weaponName, type(17, 30, Color.makeRGB(79, 89, 103)))
                            }
                            element(CssStyle(width = px(116), height = px(68))) {
                                text(usagePercent(character.usageRate), type(23, 36, blue).copy(fontWeight = 800,
                                    textAlign = TextAlign.END), id = "character-usage-$index")
                                text("胜率 ${usagePercent(character.winRate)}", type(16, 30, Color.makeRGB(79, 89, 103)).copy(
                                    textAlign = TextAlign.END))
                            }
                        }
                    }
                }
            }
            text("数据来源 DAK.GG · 使用率为该角色/武器对局中选择此装备的比例", type(16, 40, Color.makeRGB(79, 89, 103)))
        })
    }
}
