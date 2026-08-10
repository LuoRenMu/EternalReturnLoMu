package cn.luorenmu.nutdraw.templates

import cn.luorenmu.nutdraw.css.*
import cn.luorenmu.nutdraw.dom.ElementBuilder
import cn.luorenmu.nutdraw.dom.document
import cn.luorenmu.service.entity.CharacterStats
import org.jetbrains.skia.Color

/** Faithful reconstruction of character_stats.ftl + character_stats.css. */
class CharacterStatsTemplate : ImageTemplate<CharacterStats> {
    private val bg = Color.makeRGB(245,246,250); private val white = Color.WHITE; private val ink = Color.makeRGB(26,29,40)
    private val muted = Color.makeRGB(120,128,160); private val line = Color.makeRGB(226,229,238)
    override fun build(data: CharacterStats): TemplateDocument {
        val height = 150 + 52 + data.players.size * 81 + 28
        val base = data.httpServer
        return TemplateDocument(1250, height, document(CssStyle(width = px(1250), height = px(height), padding = Edges(22f), gap = 28f, background = bg, color = ink)) {
            element(CssStyle(width = percent(100), height = px(100), gap = 18f, alignItems = AlignItems.CENTER)) {
                element(CssStyle(direction = FlexDirection.ROW, width = percent(100), height = px(45), justifyContent = JustifyContent.CENTER, alignItems = AlignItems.CENTER, gap = 12f)) {
                    element(CssStyle(width = px(32), height = px(32), background = Color.makeRGB(108,92,231), borderRadius = 8f))
                    text("Character Stats", CssStyle(width = px(260), height = px(44), fontSize = 32f, color = ink))
                }
                element(CssStyle(direction = FlexDirection.ROW, width = percent(100), height = px(58), justifyContent = JustifyContent.CENTER, gap = 16f)) {
                    statBadge("统计对局", data.totalGames); statBadge("统计玩家", data.totalPlayers); statBadge("统计段位", data.tier)
                }
            }
            element(CssStyle(width = percent(100), flexGrow = 1f, background = white, border = Border(1f,line), borderRadius = 16f, gap = 0f)) {
                element(CssStyle(direction = FlexDirection.ROW, width = percent(100), height = px(52), padding = Edges(0f,14f), alignItems = AlignItems.CENTER, background = Color.makeRGB(248,249,252), border = Border(1f,Color.makeRGB(232,235,242)))) {
                    listOf("#" to 64f, "CHARACTER" to 250f, "TIER" to 72f, "RP" to 80f, "PICK RATE" to 135f, "WIN RATE" to 135f, "TOP3 RATE" to 130f, "AVG.RANK" to 105f, "AVG.DMG" to 105f, "PLAY COUNT" to 120f).forEach { (label,w) -> text(label, headerCell(w)) }
                }
                data.players.forEach { player ->
                    element(CssStyle(direction = FlexDirection.ROW, width = percent(100), height = px(81), padding = Edges(14f), alignItems = AlignItems.CENTER, border = Border(1f,Color.makeRGB(240,241,246)))) {
                        text(player.rank, CssStyle(width = px(64), height = px(38), padding = Edges(9f), fontSize = 14f, color = white, textAlign = TextAlign.CENTER, background = rankColor(player.rank), borderRadius = 19f))
                        element(CssStyle(direction = FlexDirection.ROW, width = px(250), height = px(53), alignItems = AlignItems.CENTER, gap = 14f)) {
                            element(CssStyle(width=px(58),height=px(58))) {
                                image(player.characterImgUrl.resolve(base), CssStyle(width = px(52), height = px(52), borderRadius = 26f, border = Border(2f,line), objectFit = ObjectFit.COVER))
                                element(CssStyle(width=px(22),height=px(22),padding=Edges(3f),position=Position.ABSOLUTE,right=0f,bottom=0f,background=Color.BLACK,border=Border(2f,line),borderRadius=11f)) {
                                    image(player.weaponImgUrl.resolve(base),CssStyle(width=percent(100),height=percent(100),objectFit=ObjectFit.CONTAIN))
                                }
                            }
                            text(player.characterName, cell(14f, ink, 180f).copy(textAlign = TextAlign.START))
                        }
                        text(player.tier, CssStyle(width = px(72), height = px(36), padding = Edges(8f), fontSize = 16f, color = white, textAlign = TextAlign.CENTER, background = tierColor(player.tier), borderRadius = 8f))
                        text(player.rp, cell(15f, Color.makeRGB(42,46,58),80f))
                        text(player.pick, barCell(135f, Color.makeARGB(80,100,140,240)))
                        text(player.winRate, barCell(135f, Color.makeARGB(95,80,200,120)))
                        text(player.top3Rate, cell(13f,Color.makeRGB(58,62,74),130f))
                        text(player.avgRank, cell(13f,Color.makeRGB(58,62,74),105f))
                        text(player.avgDmg, cell(15f,Color.makeRGB(224,112,48),105f))
                        text(player.playCount, cell(13f,Color.makeRGB(58,62,74),120f))
                    }
                }
            }
        })
    }
    private fun ElementBuilder.statBadge(label: String, value: Any?) { element(CssStyle(width = px(170), height = px(58), padding = Edges(8f,24f), background = white, border = Border(1f,line), borderRadius = 12f, alignItems = AlignItems.CENTER)) { text(label, CssStyle(width = percent(100), height = px(17), fontSize = 11f, color = Color.makeRGB(136,144,168), textAlign = TextAlign.CENTER)); text(value, CssStyle(width = percent(100), height = px(28), fontSize = 22f, color = ink, textAlign = TextAlign.CENTER)) } }
    private fun headerCell(width: Float) = CssStyle(width = px(width), height = px(20), fontSize = 11f, color = muted, textAlign = TextAlign.CENTER)
    private fun cell(size: Float, color: Int, width: Float) = CssStyle(width = px(width), height = px(28), fontSize = size, color = color, textAlign = TextAlign.CENTER)
    private fun barCell(width: Float, color: Int) = cell(12f,Color.makeRGB(58,62,74),width).copy(height = px(26), padding = Edges(5f), background = color, borderRadius = 13f)
    private fun rankColor(rank: Int) = when(rank) { 1 -> Color.makeRGB(246,168,0); 2 -> Color.makeRGB(160,168,184); 3 -> Color.makeRGB(205,127,50); else -> Color.makeRGB(240,241,246) }
    private fun tierColor(tier: String) = when(tier.uppercase()) { "S" -> Color.makeRGB(214,48,49); "A" -> Color.makeRGB(240,100,12); "B" -> Color.makeRGB(26,170,85); "C" -> Color.makeRGB(46,111,214); else -> Color.makeRGB(93,107,130) }
    private fun String?.resolve(base: String) = this?.takeIf(String::isNotBlank)?.let { if (it.startsWith("http")) it else base.trimEnd('/') + "/" + it.trimStart('/') }
}
