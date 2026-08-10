package cn.luorenmu.nutdraw.templates

import cn.luorenmu.command.entity.CommandHelp
import cn.luorenmu.nutdraw.css.*
import cn.luorenmu.nutdraw.dom.document
import org.jetbrains.skia.Color

/** Faithful reconstruction of help.ftl. */
class HelpTemplate : ImageTemplate<CommandHelp> {
    override fun build(data: CommandHelp): TemplateDocument {
        val cardHeights = data.helps.map { 112 + if (it.optionals.isNotEmpty()) 35 else 0 }
        val height = 32 + 184 + 20 + cardHeights.sum() + (data.helps.size - 1).coerceAtLeast(0) * 12 + 80
        val bg = Color.makeRGB(15, 17, 23); val card = Color.makeRGB(26, 29, 39); val border = Color.makeRGB(42, 45, 58)
        val text = Color.makeRGB(225, 228, 237); val muted = Color.makeRGB(139, 143, 163); val accent = Color.makeRGB(108, 140, 255)
        return TemplateDocument(960, height, document(CssStyle(width = px(960), height = px(height), padding = Edges(32f, 24f, 80f, 24f), background = bg, color = text, gap = 20f)) {
            element(CssStyle(width = percent(100), height = px(184), padding = Edges(48f, 24f), alignItems = AlignItems.CENTER, gap = 8f, background = Color.makeRGB(28,31,44), border = Border(1f, border))) {
                text("LoMu-Bot 指令", CssStyle(width = percent(100), height = px(54), fontSize = 38.4f, color = text, textAlign = TextAlign.CENTER))
                text("所有可用命令的完整参考", CssStyle(width = percent(100), height = px(26), fontSize = 16f, color = muted, textAlign = TextAlign.CENTER))
                text("v2.4.0", CssStyle(width = px(86), height = px(30), padding = Edges(4f,14f), fontSize = 13.6f, color = accent, textAlign = TextAlign.CENTER, background = Color.makeARGB(30,108,140,255), border = Border(1f, Color.makeARGB(50,108,140,255)), borderRadius = 20f))
            }
            element(CssStyle(width = percent(100), flexGrow = 1f, gap = 12f)) {
                data.helps.forEachIndexed { index, help ->
                    element(CssStyle(width = percent(100), height = px(cardHeights[index]), padding = Edges(20f,24f), gap = 8f, background = card, border = Border(1f,border), borderRadius = 12f)) {
                        element(CssStyle(direction = FlexDirection.ROW, width = percent(100), height = px(28), gap = 12f, alignItems = AlignItems.CENTER)) {
                            text(help.name, CssStyle(width = px((help.name.length * 12 + 28).coerceAtLeast(80)), height = px(26), padding = Edges(2f,10f), fontSize = 16.8f, color = Color.WHITE, background = Color.makeARGB(26,108,140,255), borderRadius = 6f))
                            text("所有人", CssStyle(width = px(58), height = px(24), padding = Edges(2f,8f), fontSize = 12.5f, color = Color.makeRGB(158,206,106), background = Color.makeARGB(30,158,206,106), borderRadius = 4f, textAlign = TextAlign.CENTER))
                        }
                        text(help.description, CssStyle(width = percent(100), height = px(24), fontSize = 14.4f, color = muted))
                        if (help.optionals.isNotEmpty()) element(CssStyle(direction = FlexDirection.ROW, wrap = FlexWrap.WRAP, width = percent(100), height = px(27), gap = 8f)) {
                            help.optionals.forEach { optional -> text("${optional.name}  ${if (optional.required) "必填" else "可选"}  ${optional.description}", CssStyle(width = px((optional.name.length + optional.description.length) * 13 + 65), height = px(27), padding = Edges(3f,10f), fontSize = 12.5f, color = Color.makeRGB(205,214,244), background = Color.makeRGB(21,24,33), borderRadius = 6f)) }
                        }
                        text(help.example, CssStyle(width = percent(100), height = px(30), padding = Edges(6f,12f), fontSize = 13f, color = Color.makeRGB(158,206,106), background = bg, borderRadius = 6f))
                    }
                }
            }
        })
    }
}
