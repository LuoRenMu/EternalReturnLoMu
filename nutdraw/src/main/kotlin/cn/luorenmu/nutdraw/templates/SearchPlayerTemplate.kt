package cn.luorenmu.nutdraw.templates

import cn.luorenmu.nutdraw.css.*
import cn.luorenmu.nutdraw.dom.ElementBuilder
import cn.luorenmu.nutdraw.dom.document
import cn.luorenmu.service.entity.EternalReturnPlayRender
import org.jetbrains.skia.Color
import kotlin.math.max

/** Faithful Skia reconstruction of search_player.ftl + search_player.css. */
class SearchPlayerTemplate : ImageTemplate<EternalReturnPlayRender> {
    private val pageBg = Color.makeRGB(245, 245, 245)
    private val white = Color.WHITE
    private val ink = Color.makeRGB(32, 32, 32)
    private val muted = Color.makeRGB(128, 128, 128)
    private val line = Color.makeRGB(230, 230, 230)
    private val darkHeader = Color.makeRGB(54, 57, 68)
    private val orange = Color.makeRGB(255, 80, 11)

    override fun build(data: EternalReturnPlayRender): TemplateDocument {
        val summaryHeight = if (data.summary != null) 190 else 0
        val matchesHeight = data.matches.sumOf { 120 + (it.teamMates?.size ?: 0) * 50 }
        val leftHeight = 470 + data.characterUseStats.size * 52 + data.recentPlayers.size * 52
        val bodyHeight = max(leftHeight, summaryHeight + matchesHeight + 30)
        val height = 20 + 211 + 20 + bodyHeight + 30
        val base = data.httpServer

        return TemplateDocument(1290, height, document(CssStyle(width = px(1290), height = px(height), padding = Edges(20f), gap = 20f, background = pageBg, color = ink), id = "content-container") {
            // #header: 177px banner + 34px attribution strip.
            element(CssStyle(width = px(1250), height = px(211), gap = 0f), id = "header") {
                element(CssStyle(direction = FlexDirection.ROW, width = percent(100), height = px(177), background = darkHeader,
                    backgroundImage = data.bannerUrl.resolve(base), border = Border(1f, darkHeader), borderRadius = 10f, alignItems = AlignItems.CENTER)) {
                    image(data.profileImageUrl.resolve(base), CssStyle(width = px(222), height = px(177), objectFit = ObjectFit.COVER))
                    element(CssStyle(width = px(430), height = px(120), margin = Edges(30f, 0f, 0f, 20f), gap = 8f)) {
                        text("Lv.${data.level}", chipStyle())
                        text(data.nickName, textStyle(28f, white, 38f))
                        text("如对该UI有任何建议或问题,欢迎加入654087758群聊反馈 ξ( ✿＞◡❛)", textStyle(11f, white, 18f))
                    }
                }
                text("Design inspired by DakGG • Powered by LuoRenMu", textStyle(10f, Color.makeRGB(209, 207, 207), 34f).copy(background = darkHeader, textAlign = TextAlign.CENTER, verticalAlign = VerticalAlign.CENTER, width = percent(100)), id = "describe")
            }

            element(CssStyle(direction = FlexDirection.ROW, width = px(1250), height = px(bodyHeight), gap = 20f, alignItems = AlignItems.START), id = "body") {
                element(CssStyle(width = px(370), height = px(bodyHeight), gap = 30f), id = "left") {
                    rankPanel(data, base)
                    if (data.characterUseStats.isNotEmpty()) characterTable(data, base)
                    if (data.recentPlayers.isNotEmpty()) recentPlayersTable(data, base)
                }
                element(CssStyle(width = px(860), height = px(bodyHeight), padding = Edges(20f), gap = 22f), id = "right") {
                    data.summary?.let { summary ->
                        element(panel(170f, 820f)) {
                            text("近期 ${summary.count} 场对局(排位)", textStyle(18f, ink, 50f).copy(padding = Edges(0f, 10f), border = Border(1f, line)))
                            element(CssStyle(direction = FlexDirection.ROW, width = percent(100), height = px(75), justifyContent = JustifyContent.CENTER, gap = 30f, alignItems = AlignItems.CENTER)) {
                                summaryStat("对局获胜数", summary.wins); summaryStat("平均排名", summary.avgRank)
                                summaryStat("平均团队击杀", summary.avgTk); summaryStat("平均伤害", summary.avgDmg)
                            }
                            element(CssStyle(direction = FlexDirection.ROW, width = percent(100), height = px(35), justifyContent = JustifyContent.CENTER, gap = 5f, alignItems = AlignItems.CENTER)) {
                                summary.ranks.forEach { rank -> text(if (rank == 99) "逃" else rank, rankChip(rank)) }
                            }
                        }
                    }
                    data.matches.forEach { match -> matchCard(match, base) }
                }
            }
        })
    }

    private fun ElementBuilder.rankPanel(data: EternalReturnPlayRender, base: String) {
        element(panel(455f, 370f), id = "rank") {
            text("${data.mode}(${data.season})", textStyle(20f, ink, 45f).copy(textAlign = TextAlign.CENTER, verticalAlign = VerticalAlign.CENTER, border = Border(1f, line)))
            element(CssStyle(direction = FlexDirection.ROW, width = percent(100), height = px(106), justifyContent = JustifyContent.CENTER, alignItems = AlignItems.CENTER, gap = 15f, border = Border(1f, line))) {
                image(data.data.tierImageUrl.resolve(base), CssStyle(width = px(64), height = px(64), objectFit = ObjectFit.CONTAIN))
                element(CssStyle(width = px(190), height = px(65), gap = 4f, alignItems = AlignItems.CENTER, justifyContent = JustifyContent.CENTER)) {
                    text(data.data.rp, textStyle(25f, Color.makeRGB(202, 147, 114), 34f).copy(textAlign = TextAlign.CENTER))
                    text(data.data.rpName, textStyle(12f, ink, 20f).copy(textAlign = TextAlign.CENTER))
                }
            }
            element(CssStyle(direction = FlexDirection.ROW, wrap = FlexWrap.WRAP, width = percent(100), height = px(178), padding = Edges(10f), gap = 5f, justifyContent = JustifyContent.CENTER), id = "rank_overview") {
                val records = listOf("平均TK" to data.data.avgTk, "TOP 1" to data.data.top1, "游戏场次" to data.data.play,
                    "平均击杀" to data.data.avgKill, "TOP 2" to data.data.top2, "平均伤害" to data.data.avgDmg,
                    "平均助攻" to data.data.avgAssists, "TOP 3" to data.data.top3, "平均排名" to data.data.avgRank,
                    "平均野怪击杀" to data.data.avgAnimal, "平均Credit获得" to data.data.avgCredit, "平均视野贡献" to data.data.avgVision)
                records.forEach { (label, value) -> element(CssStyle(width = px(105), height = px(40), padding = Edges(3f), background = Color.makeRGB(240,233,233), borderRadius = 5f, alignItems = AlignItems.CENTER)) { text(label, textStyle(11f, ink, 16f).copy(textAlign = TextAlign.CENTER)); text(value, textStyle(13f, orange, 18f).copy(textAlign = TextAlign.CENTER)) } }
            }
            data.mmrStats?.let { mmr ->
                element(CssStyle(width = percent(100), height = px(115), margin = Edges(top = 10f), padding = Edges(12f), border = Border(1f, line)), id = "rank_stats") {
                    lineChart(mmr.mmrDate, mmr.mmr, Color.makeRGB(202,164,40), CssStyle(width = percent(100), height = percent(100)), id = "rank_canvas")
                }
            }
        }
    }

    private fun ElementBuilder.matchCard(match: EternalReturnPlayRender.EternalReturnPlayerMatchData, base: String) {
        val isRank = match.type == "排位"
        val isCobalt = match.type == "钴协议"
        val accent = when (match.rank) {
            1 -> Color.makeRGB(17,178,136)
            2 -> Color.makeRGB(32,122,199)
            99 -> Color.makeRGB(71,84,130)
            else -> Color.makeRGB(75,82,93)
        }
        val cardHeight = 98f + (match.teamMates?.size ?: 0) * 50f
        element(CssStyle(width = px(810), height = px(cardHeight), background = Color.makeARGB(220,255,255,255), border = Border(1f, line), borderRadius = 20f)) {
            element(CssStyle(position = Position.ABSOLUTE, left = 0f, top = 0f, width = px(15), height = percent(100), background = accent, cornerRadii = CornerRadii(20f, 0f, 0f, 20f)))
            element(CssStyle(position = Position.ABSOLUTE, right = 0f, top = 0f, width = px(15), height = percent(100), background = accent, cornerRadii = CornerRadii(0f, 20f, 20f, 0f)))
            element(CssStyle(direction = FlexDirection.ROW, width = percent(100), height = px(98), alignItems = AlignItems.CENTER, padding = Edges(0f, 20f), gap = 14f)) {
            element(CssStyle(width = px(70), height = px(75), gap = 3f, margin = Edges(left = 3f))) {
                text(if (match.rank == 99) "逃离" else "#${match.rank}", recordTextStyle(14f, accent, 18f))
                text(match.type, recordTextStyle(12f, ink, 16f)); text(match.dateHour, recordTextStyle(11f, muted, 15f)); text(match.dateMonth, recordTextStyle(11f, muted, 15f))
            }
            element(CssStyle(width = px(70), height = px(90), gap = 4f, alignItems = AlignItems.CENTER)) {
                element(CssStyle(width=px(64),height=px(64))) {
                    image(match.characterAvatarUrl.resolve(base), CssStyle(width = px(64), height = px(64), borderRadius = 32f, objectFit = ObjectFit.COVER))
                    text(match.level,CssStyle(width=px(20),height=px(20),position=Position.ABSOLUTE,right=0f,bottom=0f,fontSize=9f,color=Color.makeRGB(50,50,50),background=Color.WHITE,border=Border(1f,Color.makeRGB(50,50,50)),borderRadius=10f,textAlign=TextAlign.CENTER,verticalAlign=VerticalAlign.CENTER))
                }
                text(match.characterName, recordTextStyle(12f, ink, 18f).copy(textAlign = TextAlign.CENTER))
            }
            element(CssStyle(width = px(55), height = px(55), direction = FlexDirection.ROW, wrap = FlexWrap.WRAP, gap = 3f)) {
                element(CssStyle(width = px(24), height = px(24), padding = Edges(2f), background = Color.makeRGB(50,50,50), borderRadius = 12f)) {
                    image(match.weaponUrl.resolve(base), CssStyle(width = percent(100), height = percent(100), objectFit = ObjectFit.CONTAIN))
                }
                listOf(match.traitSkillUrl, match.tacticalSkillUrl, match.traitSkillGroupUrl).forEach { source ->
                    element(CssStyle(width = px(24), height = px(24), background = Color.makeRGB(240,241,246), borderRadius = 12f)) {
                        image(source.resolve(base), CssStyle(width = percent(100), height = percent(100), borderRadius = 12f, objectFit = ObjectFit.CONTAIN))
                    }
                }
            }
            element(CssStyle(direction = FlexDirection.ROW, width = px(350), height = px(75), justifyContent = JustifyContent.SPACE_BETWEEN, alignItems = AlignItems.CENTER)) {
                metric("${match.tk} / ${match.kill} / ${match.assist}", "TK / K / A")
                metric(match.dmg, "DMG")
                when {
                    isRank -> metric("${match.rp} (${if (match.rpChange >= 0) "+" else ""}${match.rpChange})", "RP")
                    !isCobalt -> metric(String.format("%.2f", match.kda), "KDA")
                }
                if (isCobalt) infusionMetric(match, base) else metric(match.routeId, "路径ID")
            }
            element(CssStyle(direction = FlexDirection.ROW, wrap = FlexWrap.WRAP, width = px(128), height = px(55), gap = 2f, justifyContent = JustifyContent.CENTER)) {
                match.equips.take(5).forEach { equip ->
                    element(CssStyle(width = px(40), height = px(25), padding = Edges(2f), background = line, backgroundImage = equip.itemBgUrl.resolve(base), borderRadius = 4f)) {
                        image(equip.itemUrl.resolve(base), CssStyle(width = percent(100), height = percent(100), objectFit = ObjectFit.CONTAIN))
                    }
                }
            }
            }
        }
    }

    private fun ElementBuilder.characterTable(data: EternalReturnPlayRender, base: String) {
        val height = 40f + data.characterUseStats.size * 52f
        element(panel(height, 370f)) {
            text("角色       RP       平均排名   平均伤害", textStyle(12f, white, 40f).copy(background = darkHeader, padding = Edges(0f, 10f)))
            data.characterUseStats.forEach { c -> element(CssStyle(direction = FlexDirection.ROW, width = percent(100), height = px(52), padding = Edges(5f, 10f), alignItems = AlignItems.CENTER, gap = 8f, border = Border(1f, line))) { image(c.imgUrl.resolve(base), CssStyle(width = px(40), height = px(40), borderRadius = 20f)); text("${c.characterName}\n${c.characterPlay} 游戏(${c.winRate})", textStyle(12f, ink, 38f).copy(width = px(145))); text(c.getRP, textStyle(12f, if (c.getRP >= 0) Color.RED else Color.makeRGB(83,147,202), 22f).copy(width = px(48))); text(c.avgRank, textStyle(12f, ink, 22f).copy(width = px(60))); text(c.avgDmg, textStyle(12f, muted, 22f)) } }
        }
    }

    private fun ElementBuilder.recentPlayersTable(data: EternalReturnPlayRender, base: String) {
        val height = 40f + data.recentPlayers.size * 52f
        element(panel(height, 370f)) {
            text("一起游戏的玩家        胜率     平均排名", textStyle(12f, white, 40f).copy(background = darkHeader, padding = Edges(0f, 10f)))
            data.recentPlayers.forEach { p -> element(CssStyle(direction = FlexDirection.ROW, width = percent(100), height = px(52), padding = Edges(5f, 10f), alignItems = AlignItems.CENTER, gap = 8f, border = Border(1f, line))) { image(p.imageWrapperUrl.resolve(base), CssStyle(width = px(40), height = px(40), borderRadius = 20f)); text("${p.nickname}  ${p.plays} 游戏", textStyle(12f, ink, 24f).copy(width = px(190))); text(p.winRate, textStyle(12f, ink, 22f).copy(width = px(55))); text(p.avgRank, textStyle(12f, ink, 22f)) } }
        }
    }

    private fun ElementBuilder.summaryStat(label: String, value: Any?) { element(CssStyle(width = px(140), height = px(55), alignItems = AlignItems.CENTER)) { text(label, textStyle(14f, muted, 22f).copy(textAlign = TextAlign.CENTER)); text(value, textStyle(20f, ink, 28f).copy(textAlign = TextAlign.CENTER)) } }
    private fun ElementBuilder.metric(value: Any?, label: String) { element(CssStyle(width = px(72), height = px(48), alignItems = AlignItems.CENTER, justifyContent = JustifyContent.CENTER)) { text(value, recordTextStyle(14f, ink, 22f).copy(textAlign = TextAlign.CENTER)); text(label, recordTextStyle(12f, muted, 18f).copy(textAlign = TextAlign.CENTER)) } }
    private fun ElementBuilder.infusionMetric(match: EternalReturnPlayRender.EternalReturnPlayerMatchData, base: String) {
        element(CssStyle(width = px(72), height = px(55), alignItems = AlignItems.CENTER, justifyContent = JustifyContent.CENTER), id = "infusions-${match.gameId}") {
            element(CssStyle(direction = FlexDirection.ROW, wrap = FlexWrap.WRAP, width = px(60), height = px(38), gap = 2f, justifyContent = JustifyContent.CENTER)) {
                match.infusions.orEmpty().take(3).forEachIndexed { index, infusion ->
                    element(CssStyle(width = px(18), height = px(18), background = Color.makeRGB(240, 241, 246), borderRadius = 3f), id = "infusion-${match.gameId}-$index") {
                        if (infusion.imageUrl.isNotBlank()) {
                            image(infusion.imageUrl.resolve(base), CssStyle(width = percent(100), height = percent(100), borderRadius = 3f, objectFit = ObjectFit.COVER))
                        }
                        if (infusion.count > 1) {
                            text(infusion.count, CssStyle(position = Position.ABSOLUTE, right = 0f, bottom = 0f, width = px(10), height = px(10), fontSize = 8f, fontWeight = 700, color = white, background = Color.makeARGB(190, 0, 0, 0), textAlign = TextAlign.CENTER, verticalAlign = VerticalAlign.CENTER))
                        }
                    }
                }
            }
            text("灌注", recordTextStyle(12f, muted, 17f).copy(textAlign = TextAlign.CENTER))
        }
    }
    private fun panel(height: Float, width: Float) = CssStyle(width = px(width), height = px(height), background = white, border = Border(1f, line), borderRadius = 10f, gap = 0f)
    private fun textStyle(size: Float, color: Int, height: Float) = CssStyle(width = percent(100), height = px(height), fontSize = size, color = color)
    private fun recordTextStyle(size: Float, color: Int, height: Float) = textStyle(size, color, height).copy(verticalAlign = VerticalAlign.CENTER)
    private fun chipStyle() = textStyle(12f, white, 24f).copy(width = px(70), border = Border(2f, white), borderRadius = 15f, textAlign = TextAlign.CENTER,verticalAlign = VerticalAlign.CENTER)
    private fun rankChip(rank: Int) = textStyle(12f, if (rank > 3 && rank != 99) muted else white, 24f).copy(width = px(24), background = when (rank) { 1 -> Color.makeRGB(17,178,136); 2,3 -> Color.makeRGB(32,122,199); 99 -> Color.makeRGB(71,84,130); else -> Color.makeRGB(214,214,214) }, borderRadius = 3f, textAlign = TextAlign.CENTER)
    private fun String?.resolve(base: String): String? = this?.takeIf(String::isNotBlank)?.let { if (it.startsWith("http://") || it.startsWith("https://")) it else base.trimEnd('/') + "/" + it.trimStart('/') }
}
