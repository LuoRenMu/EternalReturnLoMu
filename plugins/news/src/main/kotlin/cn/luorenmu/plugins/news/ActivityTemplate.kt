package cn.luorenmu.plugins.news

import cn.luorenmu.command.entity.RedemptionCodeActivityPage
import cn.luorenmu.nutdraw.css.*
import cn.luorenmu.nutdraw.dom.document
import cn.luorenmu.nutdraw.template.ImageTemplate
import cn.luorenmu.nutdraw.template.TemplateDocument
import org.jetbrains.skia.Color

/** Faithful reconstruction of redemption_code_activity.ftl. */
class ActivityTemplate : ImageTemplate<RedemptionCodeActivityPage> {
    override fun build(data: RedemptionCodeActivityPage): TemplateDocument {
        val height = 108 + data.items.size * 184 + 28
        val bg = Color.makeRGB(17,19,21); val card = Color.makeRGB(26,29,31); val text = Color.makeRGB(236,231,223)
        val muted = Color.makeRGB(168,176,170); val border = Color.makeARGB(30,236,231,223)
        return TemplateDocument(1040,height, document(CssStyle(width=px(1040),height=px(height),padding=Edges(28f),gap=14f,background=bg,color=text)) {
            element(CssStyle(direction=FlexDirection.ROW,width=percent(100),height=px(66),padding=Edges(0f,0f,18f,0f),justifyContent=JustifyContent.SPACE_BETWEEN,alignItems=AlignItems.END,border=Border(1f,border))) {
                element(CssStyle(width=px(780),height=px(58),gap=5f)) { text("游戏活动",CssStyle(width=percent(100),height=px(42),fontSize=34f,color=text));text("有效期内与刚过期 1 天的永恒轮回官方活动 · ${data.generatedDate}",CssStyle(width=percent(100),height=px(22),fontSize=15f,color=muted)) }
                text("${data.items.size} 条",CssStyle(width=px(112),height=px(40),fontSize=28f,color=Color.makeRGB(255,209,102),textAlign=TextAlign.END))
            }
            data.items.forEach { item ->
                element(CssStyle(direction=FlexDirection.ROW,width=percent(100),height=px(170),padding=Edges(14f),gap=18f,background=card,border=Border(1f,border),borderRadius=8f,alignItems=AlignItems.START)) {
                    if(item.thumbnailUrl.isNullOrBlank()) text("NO IMAGE",CssStyle(width=px(260),height=px(142),padding=Edges(62f),fontSize=14f,color=Color.makeRGB(119,129,125),background=Color.makeRGB(36,40,42),borderRadius=6f,textAlign=TextAlign.CENTER))
                    else image(item.thumbnailUrl,CssStyle(width=px(260),height=px(142),borderRadius=6f,objectFit=ObjectFit.COVER))
                    element(CssStyle(flexGrow=1f,height=px(142),gap=8f)) {
                        element(CssStyle(direction=FlexDirection.ROW,width=percent(100),height=px(32),justifyContent=JustifyContent.SPACE_BETWEEN,alignItems=AlignItems.START,gap=16f)) {
                            text(item.title,CssStyle(flexGrow=1f,height=px(30),fontSize=22f,color=Color.makeRGB(255,250,240)))
                            text(item.status,CssStyle(width=px(95),height=px(25),padding=Edges(4f,10f),fontSize=13f,color=Color.makeRGB(15,21,19),background=if(item.status.contains("过期"))Color.makeRGB(255,179,71) else Color.makeRGB(89,211,155),borderRadius=6f,textAlign=TextAlign.CENTER))
                        }
                        item.code?.takeIf(String::isNotBlank)?.let { text(it,CssStyle(width=px((it.length*13+28).coerceAtLeast(140)),height=px(38),padding=Edges(6f,12f),fontSize=18f,color=Color.makeRGB(123,223,242),background=Color.makeRGB(14,16,17),border=Border(1f,Color.makeARGB(70,123,223,242)),borderRadius=6f)) }
                        item.reward.takeIf(String::isNotBlank)?.let { text("奖励     $it",CssStyle(width=percent(100),height=px(19),fontSize=13f,color=muted)) }
                        item.note.takeIf(String::isNotBlank)?.let { text("说明     $it",CssStyle(width=percent(100),height=px(19),fontSize=13f,color=muted)) }
                        text("有效期   ${item.period}",CssStyle(width=percent(100),height=px(19),fontSize=13f,color=muted))
                    }
                }
            }
        })
    }
}
