package cn.luorenmu.command.template

import cn.luorenmu.command.entity.CommandHelp
import cn.luorenmu.nutdraw.css.*
import cn.luorenmu.nutdraw.dom.ElementBuilder
import cn.luorenmu.nutdraw.dom.document
import cn.luorenmu.nutdraw.template.ImageTemplate
import cn.luorenmu.nutdraw.template.TemplateDocument
import org.jetbrains.skia.Color

/** Bright, cartoon-styled command guide with strictly bounded card layouts. */
class HelpTemplate : ImageTemplate<CommandHelp> {
    private val page = Color.makeRGB(255, 247, 251)
    private val ink = Color.makeRGB(82, 55, 73)
    private val muted = Color.makeRGB(137, 101, 122)
    private val purple = Color.makeRGB(151, 111, 214)
    private val pink = Color.makeRGB(238, 123, 174)
    private val green = Color.makeRGB(72, 157, 118)
    private val cardColors = listOf(
        Color.makeRGB(255, 238, 246),
        Color.makeRGB(243, 238, 255),
        Color.makeRGB(235, 248, 255),
        Color.makeRGB(255, 246, 226),
    )

    override fun build(data: CommandHelp): TemplateDocument {
        val cardHeights = data.helps.map(::cardHeight)
        val rowHeights = cardHeights.chunked(2).map { it.max() }
        val rowsGap = (rowHeights.size - 1).coerceAtLeast(0) * 14
        val gridHeight = rowHeights.sum() + rowsGap
        val height = 28 + 190 + 22 + gridHeight + 32

        return TemplateDocument(1000, height, document(
            CssStyle(width = px(1000), height = px(height), padding = Edges(28f, 32f, 32f, 32f), gap = 22f, background = page, color = ink),
            id = "help-root",
        ) {
            header()
            element(CssStyle(direction = FlexDirection.ROW, wrap = FlexWrap.WRAP, width = px(936), height = px(gridHeight.toFloat()), gap = 14f, alignItems = AlignItems.START), id = "command-list") {
                data.helps.forEachIndexed { index, help -> commandCard(index, help, cardHeights[index].toFloat()) }
            }
        })
    }

    private fun ElementBuilder.header() {
        element(CssStyle(width = px(936), height = px(190), padding = Edges(24f), alignItems = AlignItems.CENTER, justifyContent = JustifyContent.CENTER, gap = 8f, background = Color.makeRGB(255, 224, 238), border = Border(2f, Color.makeRGB(247, 173, 207)), borderRadius = 28f), id = "help-header") {
            element(CssStyle(position = Position.ABSOLUTE, left = 30f, top = 26f, width = px(54), height = px(54), background = Color.makeARGB(80, 255, 255, 255), borderRadius = 27f), id = "header-bubble-left")
            element(CssStyle(position = Position.ABSOLUTE, right = 38f, top = 34f, width = px(34), height = px(34), background = Color.makeARGB(90, 151, 111, 214), borderRadius = 17f), id = "header-bubble-right")
            text("LoMu-Bot 指令参考", CssStyle(width = px(760), height = px(52), fontSize = 34f, fontWeight = 700, color = ink, textAlign = TextAlign.CENTER, verticalAlign = VerticalAlign.CENTER), id = "help-title")
            text("所有的命令不强制要求携带/", CssStyle(width = px(700), height = px(28), fontSize = 16f, color = muted, textAlign = TextAlign.CENTER, verticalAlign = VerticalAlign.CENTER), id = "help-subtitle")
            text("v3", CssStyle(width = px(92), height = px(30), fontSize = 13f, fontWeight = 700, color = Color.WHITE, textAlign = TextAlign.CENTER, verticalAlign = VerticalAlign.CENTER, background = purple, borderRadius = 15f), id = "help-version")
        }
    }

    private fun ElementBuilder.commandCard(index: Int, help: CommandHelp.CommandHelpItem, height: Float) {
        element(CssStyle(width = px(461), height = px(height), padding = Edges(12f, 16f), gap = 6f, background = cardColors[index % cardColors.size], border = Border(2f, Color.makeARGB(125, 151, 111, 214)), borderRadius = 22f), id = "command-card-$index") {
            element(CssStyle(direction = FlexDirection.ROW, width = px(425), height = px(34), gap = 10f, alignItems = AlignItems.CENTER), id = "command-heading-$index") {
                text((index + 1).toString().padStart(2, '0'), CssStyle(width = px(34), height = px(34), fontSize = 12f, fontWeight = 700, color = Color.WHITE, textAlign = TextAlign.CENTER, verticalAlign = VerticalAlign.CENTER, background = if (index % 2 == 0) pink else purple, borderRadius = 17f))
                text(help.name, CssStyle(width = px(289), height = px(34), fontSize = 19f, fontWeight = 700, color = ink, verticalAlign = VerticalAlign.CENTER), id = "command-name-$index")
                text("全员", CssStyle(width = px(82), height = px(26), fontSize = 12f, fontWeight = 700, color = green, textAlign = TextAlign.CENTER, verticalAlign = VerticalAlign.CENTER, background = Color.makeARGB(42, 72, 157, 118), border = Border(1f, Color.makeARGB(90, 72, 157, 118)), borderRadius = 13f))
            }
            text(help.description, CssStyle(width = px(425), height = px(24), fontSize = 14f, color = muted, verticalAlign = VerticalAlign.CENTER), id = "command-description-$index")
            help.optionals.forEachIndexed { rowIndex, optional ->
                element(CssStyle(direction = FlexDirection.ROW, width = px(425), height = px(30), alignItems = AlignItems.CENTER), id = "option-row-$index-$rowIndex") {
                    text("${optional.name} · ${if (optional.required) "必填" else "可选"} · ${optional.description}", CssStyle(width = px(425), height = px(30), padding = Edges(0f, 10f), fontSize = 11.5f, color = ink, verticalAlign = VerticalAlign.CENTER, background = Color.makeARGB(150, 255, 255, 255), border = Border(1f, Color.makeARGB(70, 151, 111, 214)), borderRadius = 10f))
                }
            }
            element(CssStyle(direction = FlexDirection.ROW, width = px(425), height = px(34), padding = Edges(0f, 10f), alignItems = AlignItems.CENTER, gap = 8f, background = Color.makeARGB(175, 255, 255, 255), borderRadius = 11f), id = "command-example-$index") {
                text("▶", CssStyle(width = px(22), height = px(24), fontSize = 12f, fontWeight = 700, color = pink, textAlign = TextAlign.CENTER, verticalAlign = VerticalAlign.CENTER))
                text(help.example, CssStyle(width = px(375), height = px(24), fontSize = 13f, fontWeight = 700, color = green, verticalAlign = VerticalAlign.CENTER))
            }
        }
    }

    private fun cardHeight(help: CommandHelp.CommandHelpItem): Int {
        val optionRows = help.optionals.size
        return 128 + optionRows * 36
    }
}
