package cn.luorenmu.plugins.equipment

import cn.luorenmu.nutdraw.css.*
import cn.luorenmu.nutdraw.dom.document
import cn.luorenmu.nutdraw.template.ImageTemplate
import cn.luorenmu.nutdraw.template.TemplateDocument
import cn.luorenmu.request.api.entity.response.dakgg.DakGGItemsResponse.Item
import org.jetbrains.skia.Color

data class ItemQueryData(val item: Item, val materials: List<Item>)

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
        val materialRows = data.materials.chunked(3)
        val materialsHeight = if (materialRows.isEmpty()) 40 else materialRows.size * 104
        val height = 64 + 152 + 52 + lines.size * 32 + 60 + materialsHeight + 40
        val ink = Color.makeRGB(24, 29, 36)
        val blue = Color.makeRGB(35, 78, 181)
        fun type(size: Int, height: Int, color: Int = ink) = CssStyle(
            width = percent(100), height = px(height), fontSize = size.toFloat(), color = color,
            verticalAlign = VerticalAlign.CENTER)
        return TemplateDocument(1000, height, document(CssStyle(
            width = px(1000), height = px(height), padding = Edges(32f), background = Color.WHITE,
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
            text("直接合成材料", type(26, 60, blue).copy(fontWeight = 700))
            if (materialRows.isEmpty()) text("无合成配方（基础装备或特殊来源）", type(22, 40))
            materialRows.forEachIndexed { rowIndex, row ->
                element(CssStyle(direction = FlexDirection.ROW, width = percent(100), height = px(104), gap = 18f)) {
                    row.forEachIndexed { index, material ->
                        val id = rowIndex * 3 + index
                        element(CssStyle(direction = FlexDirection.ROW, width = px(300), height = px(92),
                            gap = 12f, padding = Edges(12f), alignItems = AlignItems.CENTER,
                            background = Color.makeRGB(240, 243, 248), borderRadius = 8f), id = "material-$id") {
                            element(CssStyle(width = px(102), height = px(64),
                                backgroundImage = equipmentBackground(material.grade)), id = "material-background-$id") {
                                if (material.imageUrl.isNotBlank()) {
                                    image(material.imageUrl, CssStyle(width = percent(100), height = percent(100),
                                        objectFit = ObjectFit.CONTAIN), id = "material-image-$id")
                                } else text("暂无图片", type(16, 64).copy(textAlign = TextAlign.CENTER))
                            }
                            text(material.name, type(20, 68).copy(width = CssSize.Auto, flexGrow = 1f,
                                fontWeight = 600), id = "material-name-$id")
                        }
                    }
                }
            }
            text("数据来源 DAK.GG · 装备资料（非对局统计）", type(16, 40, Color.makeRGB(79, 89, 103)))
        })
    }
}
