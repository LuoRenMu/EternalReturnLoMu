package cn.luorenmu.nutdraw.templates

import cn.luorenmu.nutdraw.css.*
import cn.luorenmu.nutdraw.dom.ElementBuilder
import cn.luorenmu.nutdraw.dom.document
import cn.luorenmu.service.entity.TierStatistics
import org.jetbrains.skia.Color
import kotlin.math.ceil

/** Faithful reconstruction of tier_statistics_number.ftl. */
class TierStatisticsTemplate : ImageTemplate<TierStatistics> {
    override fun build(data: TierStatistics): TemplateDocument {
        val rows = ceil(data.tierTypes.size / 5.0).toInt()
        val height = 170 + 450 + rows * 230 + 60
        val bg = Color.makeRGB(28,27,32); val card = Color.makeRGB(39,38,44); val muted = Color.makeRGB(156,155,161)
        return TemplateDocument(1360, height, document(CssStyle(width = px(1360), height = px(height), padding = Edges(30f), gap = 20f, background = bg, borderRadius = 50f, color = Color.WHITE, alignItems = AlignItems.CENTER)) {
            text(data.season, CssStyle(width = percent(100), height = px(62), fontSize = 48f, color = Color.WHITE, textAlign = TextAlign.CENTER))
            text("生成时间 ${data.date}", CssStyle(width = percent(100), height = px(48), fontSize = 32f, color = muted, textAlign = TextAlign.CENTER))
            element(CssStyle(direction = FlexDirection.ROW, width = percent(100), height = px(410), justifyContent = JustifyContent.CENTER, alignItems = AlignItems.START, gap = 30f)) {
                cutoffCard("永恒", "8", data.eternal?.mmr, data.httpServer, card)
                cutoffCard("半神", "7", data.demigod?.mmr, data.httpServer, card)
            }
            element(CssStyle(direction = FlexDirection.ROW, wrap = FlexWrap.WRAP, width = percent(100), height = px(rows * 220), justifyContent = JustifyContent.CENTER, alignItems = AlignItems.START, gap = 20f)) {
                data.tierTypes.forEach { tier ->
                    element(CssStyle(width = px(210), height = px(210), padding = Edges(15f), gap = 8f, background = card, borderRadius = 20f, alignItems = AlignItems.CENTER)) {
                        image("${data.httpServer}/resources/images/tier/full/$tier.png", CssStyle(width = px(100), height = px(150), borderRadius = 15f, objectFit = ObjectFit.COVER))
                        text("${data.count[tier] ?: 0}人(占比${data.rate[tier] ?: "0"}%)", CssStyle(width = percent(100), height = px(28), fontSize = 16f, color = Color.WHITE, textAlign = TextAlign.CENTER))
                    }
                }
            }
        })
    }
    private fun ElementBuilder.cutoffCard(label: String, tier: String, mmr: Int?, server: String, card: Int) {
        element(CssStyle(width = px(290), height = px(390), padding = Edges(20f), gap = 15f, background = card, borderRadius = 20f, alignItems = AlignItems.CENTER)) {
            image("$server/resources/images/tier/full/$tier.png", CssStyle(width = px(250), height = px(300), borderRadius = 15f, objectFit = ObjectFit.COVER))
            text("$label  ${mmr?.let { "$it RP" } ?: "暂无数据"}", CssStyle(width = percent(100), height = px(42), fontSize = 25f, color = Color.WHITE, textAlign = TextAlign.CENTER))
        }
    }
}
