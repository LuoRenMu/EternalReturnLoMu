package cn.luorenmu.plugins.news

import cn.luorenmu.command.entity.RedemptionCodeActivityPage
import cn.luorenmu.nutdraw.css.*
import cn.luorenmu.nutdraw.dom.document
import cn.luorenmu.nutdraw.template.ImageTemplate
import cn.luorenmu.nutdraw.template.TemplateDocument
import org.jetbrains.skia.Color

/** 游戏活动列表图片模板。 */
class ActivityTemplate : ImageTemplate<RedemptionCodeActivityPage> {
    override fun build(data: RedemptionCodeActivityPage): TemplateDocument {
        val cardHeight = 250
        val gap = 16
        val height = 142 + data.items.size * cardHeight + (data.items.size - 1).coerceAtLeast(0) * gap
        val page = Color.makeRGB(15, 18, 20)
        val card = Color.makeRGB(25, 29, 32)
        val cardInset = Color.makeRGB(19, 22, 24)
        val ink = Color.makeRGB(242, 239, 232)
        val muted = Color.makeRGB(164, 173, 169)
        val accent = Color.makeRGB(116, 221, 190)
        val codeColor = Color.makeRGB(124, 218, 242)
        val border = Color.makeARGB(38, 236, 231, 223)

        return TemplateDocument(
            1120,
            height,
            document(
                CssStyle(
                    width = px(1120),
                    height = px(height),
                    padding = Edges(28f),
                    gap = gap.toFloat(),
                    background = page,
                    color = ink,
                )
            ) {
                element(
                    CssStyle(
                        direction = FlexDirection.ROW,
                        width = percent(100),
                        height = px(82),
                        padding = Edges(0f, 0f, 18f, 0f),
                        justifyContent = JustifyContent.SPACE_BETWEEN,
                        alignItems = AlignItems.END,
                        border = Border(1f, border),
                    )
                ) {
                    element(CssStyle(width = px(820), height = px(64), gap = 7f)) {
                        text("永恒轮回 · 游戏活动", CssStyle(width = percent(100), height = px(40), fontSize = 34f, color = ink))
                        text("按活动时间自动筛选 · 更新于 ${data.generatedDate}", CssStyle(width = percent(100), height = px(22), fontSize = 15f, color = muted))
                    }
                    text("${data.items.size} 项", CssStyle(width = px(150), height = px(40), fontSize = 28f, color = Color.makeRGB(255, 209, 102), textAlign = TextAlign.END))
                }

                data.items.forEach { item ->
                    element(
                        CssStyle(
                            direction = FlexDirection.ROW,
                            width = percent(100),
                            height = px(cardHeight),
                            padding = Edges(16f),
                            gap = 20f,
                            background = card,
                            border = Border(1f, border),
                            borderRadius = 10f,
                            alignItems = AlignItems.START,
                        )
                    ) {
                        if (item.thumbnailUrl.isNullOrBlank()) {
                            text(
                                "ETERNAL RETURN",
                                CssStyle(
                                    width = px(300),
                                    height = px(184),
                                    padding = Edges(82f, 20f),
                                    fontSize = 14f,
                                    color = Color.makeRGB(115, 128, 124),
                                    background = cardInset,
                                    borderRadius = 8f,
                                    textAlign = TextAlign.CENTER,
                                ),
                            )
                        } else {
                            image(item.thumbnailUrl, CssStyle(width = px(300), height = px(184), borderRadius = 8f, objectFit = ObjectFit.COVER))
                        }

                        element(CssStyle(flexGrow = 1f, height = px(216), gap = 7f)) {
                            element(
                                CssStyle(
                                    direction = FlexDirection.ROW,
                                    width = percent(100),
                                    height = px(38),
                                    justifyContent = JustifyContent.SPACE_BETWEEN,
                                    alignItems = AlignItems.START,
                                    gap = 16f,
                                )
                            ) {
                                text(item.title, CssStyle(flexGrow = 1f, height = px(34), fontSize = 23f, color = ink))
                                text(
                                    item.status,
                                    CssStyle(
                                        width = px(104),
                                        height = px(28),
                                        padding = Edges(5f, 10f),
                                        fontSize = 13f,
                                        color = Color.makeRGB(13, 20, 18),
                                        background = if (item.status.contains("过期")) Color.makeRGB(255, 179, 71) else accent,
                                        borderRadius = 14f,
                                        textAlign = TextAlign.CENTER,
                                    ),
                                )
                            }
                            text("活动时间  ${item.period}", CssStyle(width = percent(100), height = px(24), fontSize = 14f, color = muted))

                            val displayedCode = item.code?.takeIf(String::isNotBlank)?.let { "兑换码  $it" }
                                ?: "无兑换码 · 详见活动说明"
                            text(
                                displayedCode,
                                CssStyle(
                                    width = percent(100),
                                    height = px(38),
                                    padding = Edges(7f, 12f),
                                    fontSize = 17f,
                                    color = if (item.code.isNullOrBlank()) muted else codeColor,
                                    background = cardInset,
                                    border = Border(1f, if (item.code.isNullOrBlank()) border else Color.makeARGB(75, 124, 218, 242)),
                                    borderRadius = 7f,
                                ),
                            )

                            element(CssStyle(width = percent(100), height = px(94), padding = Edges(9f, 12f), gap = 6f, background = cardInset, borderRadius = 7f)) {
                                val reward = item.reward.takeIf(String::isNotBlank) ?: "未注明"
                                val note = item.note.takeIf(String::isNotBlank) ?: "请查看官方活动页面"
                                text("奖励  $reward", CssStyle(width = percent(100), height = px(35), fontSize = 14f, color = ink))
                                text("说明  $note", CssStyle(width = percent(100), height = px(35), fontSize = 14f, color = muted))
                            }
                        }
                    }
                }
            },
        )
    }
}
