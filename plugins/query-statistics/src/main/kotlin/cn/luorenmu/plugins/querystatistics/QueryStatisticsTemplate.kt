package cn.luorenmu.plugins.querystatistics

import cn.luorenmu.nutdraw.css.*
import cn.luorenmu.nutdraw.dom.ElementBuilder
import cn.luorenmu.nutdraw.dom.document
import cn.luorenmu.nutdraw.template.ImageTemplate
import cn.luorenmu.nutdraw.template.TemplateDocument
import org.jetbrains.skia.Color
import java.time.format.DateTimeFormatter

class QueryStatisticsTemplate : ImageTemplate<QueryStatisticsData> {
    private val background = Color.makeRGB(246, 241, 245)
    private val card = Color.WHITE
    private val ink = Color.makeRGB(67, 44, 57)
    private val muted = Color.makeRGB(147, 113, 132)
    private val accent = Color.makeRGB(218, 86, 142)
    private val line = Color.makeRGB(235, 218, 227)
    private val timeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    override fun build(data: QueryStatisticsData): TemplateDocument {
        val rowCount = data.history.size.coerceAtLeast(1)
        val height = 260 + rowCount * 72 + 70
        return TemplateDocument(900, height, document(rootStyle(height)) {
            text("玩家查询统计", textStyle(34f, ink, 52f).copy(textAlign = TextAlign.CENTER))
            text("${data.senderName} 的查询足迹", textStyle(16f, muted, 30f).copy(textAlign = TextAlign.CENTER))

            text("我查询过的玩家", textStyle(20f, ink, 34f))
            if (data.history.isEmpty()) {
                element(CssStyle(width = percent(100), height = px(72), background = card, borderRadius = 14f, alignItems = AlignItems.CENTER, justifyContent = JustifyContent.CENTER)) {
                    text("暂无记录，先使用 /查询玩家 玩家名", textStyle(15f, muted, 28f).copy(textAlign = TextAlign.CENTER))
                }
            } else {
                data.history.forEachIndexed { index, item -> historyRow(index + 1, item.nickname, item.queryCount, item.lastQueryAt.format(timeFormat)) }
            }
            text("仅统计成功返回的玩家查询", textStyle(12f, muted, 24f).copy(textAlign = TextAlign.CENTER))
        })
    }

    private fun ElementBuilder.historyRow(index: Int, nickname: String, count: Long, lastQueryAt: String) {
        element(CssStyle(direction = FlexDirection.ROW, width = percent(100), height = px(64), padding = Edges(12f, 18f), background = card, border = Border(1f, line), borderRadius = 14f, alignItems = AlignItems.CENTER)) {
            text(index.toString().padStart(2, '0'), textStyle(14f, accent, 30f).copy(width = px(50)))
            text(nickname, textStyle(18f, ink, 30f).copy(width = px(390)))
            text("查询 $count 次", textStyle(14f, accent, 30f).copy(width = px(130), textAlign = TextAlign.CENTER))
            text(lastQueryAt, textStyle(13f, muted, 30f).copy(width = px(230), textAlign = TextAlign.END))
        }
    }

    private fun rootStyle(height: Int) = CssStyle(
        width = px(900), height = px(height), padding = Edges(30f), gap = 12f,
        background = background, color = ink,
    )

    private fun textStyle(size: Float, color: Int, height: Float) = CssStyle(
        width = percent(100), height = px(height), fontSize = size, color = color,
        verticalAlign = VerticalAlign.CENTER,
    )
}
