package cn.luorenmu.plugins.player

import cn.luorenmu.nutdraw.css.*
import cn.luorenmu.nutdraw.dom.ElementBuilder
import cn.luorenmu.nutdraw.dom.Document
import cn.luorenmu.nutdraw.template.ImageTemplate
import cn.luorenmu.nutdraw.template.TemplateDocument
import cn.luorenmu.service.entity.EternalReturnPlayRender
import org.jetbrains.skia.Color
import kotlin.math.max

/**
 * Faithful Skia reconstruction of search_player.ftl + search_player.css.
 *
 * @author LoMu
 * Date 2026/8/16 15:30
 */
class SearchPlayerTemplate : ImageTemplate<EternalReturnPlayRender> {
    private val pageBg = Color.makeRGB(245, 245, 245)
    private val white = Color.WHITE
    private val ink = Color.makeRGB(32, 32, 32)
    private val muted = Color.makeRGB(128, 128, 128)
    private val line = Color.makeRGB(230, 230, 230)
    private val darkHeader = Color.makeRGB(54, 57, 68)
    private val orange = Color.makeRGB(255, 80, 11)
    private val positive = Color.makeRGB(24, 161, 108)
    private val negative = Color.makeRGB(210, 65, 65)


    override fun build(data: EternalReturnPlayRender): TemplateDocument {
        val summaryHeight = if (data.summary != null) 190f else 0f
        val matchesHeight = data.matches.sumOf { 120 + (it.teamMates?.size ?: 0) * 50 }.toFloat()
        val rankHeight = rankPanelHeight(data)
        val leftSections = buildList {
            add(rankHeight)
            if (data.characterUseStats.isNotEmpty()) add(40f + data.characterUseStats.size * 52f)
            if (data.recentPlayers.isNotEmpty()) add(40f + data.recentPlayers.size * 52f)
        }
        val leftHeight = leftSections.sum() + (leftSections.size - 1).coerceAtLeast(0) * 30f
        val bodyHeight = max(leftHeight, summaryHeight + matchesHeight + 30)
        val height = (20 + 211 + 20 + bodyHeight + 30).toInt()
        val base = data.httpServer

        return TemplateDocument(1290, height, Document(CssStyle(width = px(1290), height = px(height), padding = Edges(20f), gap = 20f, background = pageBg, color = ink), id = "content-container") {
            // #header: 177px banner + 34px attribution strip.
            Column(CssStyle(width = px(1250), height = px(211), gap = 0f), id = "header") {
                Row(CssStyle(width = percent(100), height = px(177), background = darkHeader,
                    backgroundImage = data.bannerUrl.resolve(base), border = Border(1f, darkHeader), cornerRadii = CornerRadii(10f, 10f, 0f, 0f), alignItems = AlignItems.CENTER), id = "header-banner") {
                    Image(data.profileImageUrl.resolve(base), CssStyle(width = px(222), height = px(177), cornerRadii = CornerRadii(10f, 0f, 0f, 0f), objectFit = ObjectFit.COVER), id = "profile-image")
                    Column(CssStyle(width = px(430), height = px(120), margin = Edges(30f, 0f, 0f, 20f), gap = 8f)) {
                        Text("Lv.${data.level}", chipStyle())
                        Text(data.nickName, textStyle(28f, white, 38f).copy(fontWeight = 800))
                        Text("ξ( ✿＞◡❛)", textStyle(11f, white, 18f))
                    }
                }
                Text(data.rate, textStyle(12f, Color.makeRGB(209, 207, 207), 34f).copy(background = darkHeader, textAlign = TextAlign.CENTER, verticalAlign = VerticalAlign.CENTER, width = percent(100)), id = "describe")
            }

            Row(CssStyle(width = px(1250), height = px(bodyHeight), gap = 20f, alignItems = AlignItems.START), id = "body") {
                Column(CssStyle(width = px(370), height = px(bodyHeight), gap = 30f), id = "left") {
                    rankPanel(data, base)
                    if (data.characterUseStats.isNotEmpty()) characterTable(data, base)
                    if (data.recentPlayers.isNotEmpty()) recentPlayersTable(data, base)
                }
                Column(CssStyle(width = px(860), height = px(bodyHeight), padding = Edges(20f), gap = 22f), id = "right") {
                    data.summary?.let { summary ->
                        Column(panel(170f, 820f).copy(justifyContent = JustifyContent.CENTER), id = "summary-panel") {
                            Text("近期 ${summary.count} 场对局(排位)", textStyle(18f, ink, 50f).copy(padding = Edges(0f, 10f), verticalAlign = VerticalAlign.CENTER), id = "summary-title")
                            Row(CssStyle(width = percent(100), height = px(75), justifyContent = JustifyContent.CENTER, gap = 30f, alignItems = AlignItems.CENTER), id = "summary-stats") {
                                summaryStat("对局获胜数", summary.wins); summaryStat("平均排名", summary.avgRank)
                                summaryStat("平均团队击杀", summary.avgTk); summaryStat("平均伤害", summary.avgDmg)
                            }
                            Row(CssStyle(width = percent(100), height = px(35), justifyContent = JustifyContent.CENTER, gap = 5f, alignItems = AlignItems.CENTER), id = "summary-ranks") {
                                summary.ranks.forEachIndexed { index, rank ->
                                    Text(if (rank == 99) "逃" else rank, rankChip(rank), id = "summary-rank-$index")
                                }
                            }
                        }
                    }
                    data.matches.forEach { match -> matchCard(match, base) }
                }
            }
        })
    }

    private fun ElementBuilder.rankPanel(data: EternalReturnPlayRender, base: String) {
        Column(panel(rankPanelHeight(data), 370f), id = "rank") {
            Text("${data.mode}(${data.season})", textStyle(20f, ink, 45f).copy(fontWeight = 800,textAlign = TextAlign.CENTER, verticalAlign = VerticalAlign.CENTER, border = Border(1f, line)))
            Row(CssStyle(width = percent(100), height = px(106), justifyContent = JustifyContent.CENTER, alignItems = AlignItems.CENTER, gap = 15f, border = Border(1f, line))) {
                Image(data.data.tierImageUrl.resolve(base), CssStyle(width = px(64), height = px(64), objectFit = ObjectFit.CONTAIN))
                Column(CssStyle(width = px(190), height = px(65), gap = 4f, alignItems = AlignItems.CENTER, justifyContent = JustifyContent.CENTER)) {
                    Text(data.data.rp, textStyle(25f, Color.makeRGB(202, 147, 114), 34f).copy(textAlign = TextAlign.CENTER))
                    Text(data.data.rpName, textStyle(12f, ink, 20f).copy(textAlign = TextAlign.CENTER))
                }
            }
            Row(CssStyle(wrap = FlexWrap.WRAP, width = percent(100), height = px(195), padding = Edges(10f), gap = 5f, justifyContent = JustifyContent.CENTER), id = "rank_overview") {
                val records = listOf("平均TK" to data.data.avgTk, "TOP 1" to data.data.top1, "游戏场次" to data.data.play,
                    "平均击杀" to data.data.avgKill, "TOP 2" to data.data.top2, "平均伤害" to data.data.avgDmg,
                    "平均助攻" to data.data.avgAssists, "TOP 3" to data.data.top3, "平均排名" to data.data.avgRank,
                    "平均野怪击杀" to data.data.avgAnimal, "平均Credit获得" to data.data.avgCredit, "平均视野贡献" to data.data.avgVision)
                records.forEach { (label, value) -> Column(CssStyle(width = px(105), height = px(40), padding = Edges(3f), background = Color.makeRGB(240,233,233), borderRadius = 5f, alignItems = AlignItems.CENTER)) { Text(label, textStyle(11f, ink, 16f).copy(textAlign = TextAlign.CENTER)); Text(value, textStyle(13f, orange, 18f).copy(textAlign = TextAlign.CENTER)) } }
            }
            data.mmrStats?.let { mmr ->
                Column(CssStyle(width = percent(100), height = px(230), margin = Edges(top = 10f), padding = Edges(12f), border = Border(1f, line)), id = "rank_stats") {
                    LineChart(mmr.mmrDate, mmr.mmr, Color.makeRGB(202,164,40), CssStyle(width = percent(100), height = percent(100)), id = "rank_canvas")
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
        Column(CssStyle(width = px(810), height = px(cardHeight), background = Color.makeARGB(220,255,255,255), border = Border(1f, line), borderRadius = 20f), id = "match-${match.gameId}") {
            Column(CssStyle(position = Position.ABSOLUTE, left = 0f, top = 0f, width = px(15), height = percent(100), background = accent, cornerRadii = CornerRadii(20f, 0f, 0f, 20f)))
            Column(CssStyle(position = Position.ABSOLUTE, right = 0f, top = 0f, width = px(15), height = percent(100), background = accent, cornerRadii = CornerRadii(0f, 20f, 20f, 0f)))
            Row(CssStyle(width = percent(100), height = px(98), alignItems = AlignItems.CENTER, padding = Edges(0f, 20f), gap = 14f)) {
                Column(CssStyle(width = px(50), height = px(75), gap = 3f, margin = Edges(left = 3f))) {
                    Text(if (isCobalt) if (match.rank == 1) "胜利" else "失败" else if (match.rank == 99) "逃离" else "#${match.rank}" , recordTextStyle(14f, accent, 18f))
                    Text(match.type, recordTextStyle(12f, ink, 16f))
                    Text(match.dateHour, recordTextStyle(11f, muted, 15f))
                    Text(match.dateMonth, recordTextStyle(11f, muted, 15f))
                }
                Column(CssStyle(width = px(70), height = px(90), gap = 4f, alignItems = AlignItems.CENTER)) {
                    Column(CssStyle(width = px(64), height = px(64))) {
                        Image(match.characterAvatarUrl.resolve(base), CssStyle(width = px(64), height = px(64), borderRadius = 32f, objectFit = ObjectFit.COVER))
                        Text(match.level, CssStyle(width = px(20), height = px(20), position = Position.ABSOLUTE, right = 0f, bottom = 0f, fontSize = 9f, color = Color.makeRGB(50,50,50), background = Color.WHITE, border = Border(1f,Color.makeRGB(50,50,50)), borderRadius = 10f, textAlign = TextAlign.CENTER, verticalAlign = VerticalAlign.CENTER))
                    }
                    Text(match.characterName, recordTextStyle(12f, ink, 18f).copy(textAlign = TextAlign.CENTER))
                }
                Row(CssStyle(width = px(55), height = px(55), wrap = FlexWrap.WRAP, gap = 3f)) {
                    Column(CssStyle(width = px(24), height = px(24), padding = Edges(2f), background = Color.makeRGB(50,50,50), borderRadius = 12f)) {
                        Image(match.weaponUrl.resolve(base), CssStyle(width = percent(100), height = percent(100), objectFit = ObjectFit.CONTAIN))
                    }
                    listOf(
                        match.traitSkillUrl to null,
                        match.tacticalSkillUrl to match.tacticalSkillLevel,
                        match.traitSkillGroupUrl to null,
                    ).forEach { (source, skillLevel) ->
                        Column(CssStyle(width = px(24), height = px(24), background = Color.makeRGB(240,241,246), borderRadius = 12f)) {
                            Image(source.resolve(base), CssStyle(width = percent(100), height = percent(100), borderRadius = 12f, objectFit = ObjectFit.CONTAIN))
                            if (skillLevel != null) {
                                Text(
                                    skillLevel,
                                    CssStyle(
                                        width = px(11),
                                        height = px(11),
                                        position = Position.ABSOLUTE,
                                        right = 0f,
                                        bottom = 0f,
                                        fontSize = 7f,
                                        color = Color.WHITE,
                                        background = Color.makeRGB(50, 50, 50),
                                        border = Border(1f, Color.WHITE),
                                        borderRadius = 6f,
                                        textAlign = TextAlign.CENTER,
                                        verticalAlign = VerticalAlign.CENTER,
                                    ),
                                    id = "tactical-skill-level-${match.gameId}",
                                )
                            }
                        }
                    }
                }
                Row(CssStyle(width = px(350), height = px(75), justifyContent = JustifyContent.SPACE_BETWEEN, alignItems = AlignItems.CENTER)) {
                    metric("${match.tk} / ${match.kill} / ${match.assist}", "TK / K / A")
                    metric(match.dmg, "DMG")
                    when {
                        isRank -> {
                            val change = if (match.rpChange > 0) "+${match.rpChange}" else match.rpChange.toString()
                            val color = when {
                                match.rpChange > 0 -> positive
                                match.rpChange < 0 -> negative
                                else -> muted
                            }
                            metric(
                                value = "${match.rp} ($change)",
                                label = "RP",
                                width = 108f,
                                valueColor = color,
                                id = "match-rp-${match.gameId}",
                                valueId = "match-rp-value-${match.gameId}",
                            )
                        }
                        !isCobalt -> metric(String.format("%.2f", match.kda), "KDA")
                    }
                    if (isCobalt) infusionMetric(match, base) else metric(match.routeId, "路径ID")
                }
                Row(CssStyle(wrap = FlexWrap.WRAP, width = px(128), height = px(55), gap = 2f, justifyContent = JustifyContent.CENTER)) {
                    match.equips.take(5).forEach { equip ->
                        Column(CssStyle(width = px(40), height = px(25), padding = Edges(2f), background = line, backgroundImage = equip.itemBgUrl.resolve(base), borderRadius = 4f)) {
                            Image(equip.itemUrl.resolve(base), CssStyle(width = percent(100), height = percent(100), objectFit = ObjectFit.CONTAIN))
                        }
                    }
                }
            }
        }
    }

    private fun ElementBuilder.characterTable(data: EternalReturnPlayRender, base: String) {
        val height = 40f + data.characterUseStats.size * 52f
        Column(panel(height, 370f)) {
            Row(tableHeaderRowStyle(), id = "character-header-row") {
                Text("", tableCellStyle(40f, white))
                Text("角色", tableCellStyle(115f, white))
                Text("RP", tableCellStyle(42f, white))
                Text("平均排名", tableCellStyle(62f, white))
                Text("平均伤害", tableCellStyle(59f, white))
            }
            data.characterUseStats.forEachIndexed { index, c ->
                Row(tableRowStyle(52f)) {
                    Image(c.imgUrl.resolve(base), CssStyle(width = px(40), height = px(40), borderRadius = 20f, objectFit = ObjectFit.COVER), id = "character-avatar-$index")
                    playerIdentityCell(c.characterName, "${c.characterPlay} 场游戏(${c.winRate})", 115f, "character-name-$index")
                    Text(c.getRP, tableCellStyle(42f, if (c.getRP >= 0) Color.RED else Color.makeRGB(83,147,202)))
                    Text(c.avgRank, tableCellStyle(62f, ink))
                    Text(c.avgDmg, tableCellStyle(59f, muted))
                }
            }
        }
    }

    private fun ElementBuilder.recentPlayersTable(data: EternalReturnPlayRender, base: String) {
        val height = 40f + data.recentPlayers.size * 52f
        Column(panel(height, 370f)) {
            Row(tableHeaderRowStyle(), id = "recent-header-row") {
                Text("", tableCellStyle(40f, white))
                Text("一起游玩的玩家", tableCellStyle(170f, white), id = "recent-name-header")
                Text("胜率", tableCellStyle(48f, white), id = "recent-win-rate-header")
                Text("平均排名", tableCellStyle(68f, white), id = "recent-rank-header")
            }
            data.recentPlayers.forEachIndexed { index, p ->
                Row(tableRowStyle(52f)) {
                    Image((p.imageWrapperUrl.takeIf(String::isNotBlank) ?: "/static/images/character-null.png").resolve(base), CssStyle(width = px(40), height = px(40), borderRadius = 20f, objectFit = ObjectFit.COVER), id = "recent-avatar-$index")
                    playerIdentityCell(p.nickname, "${p.plays} 场游戏", 170f, "recent-name-$index")
                    Text(p.winRate, tableCellStyle(48f, ink), id = "recent-win-rate-$index")
                    Text(p.avgRank, tableCellStyle(68f, ink), id = "recent-rank-$index")
                }
            }
        }
    }

    private fun ElementBuilder.playerIdentityCell(primary: String, secondary: String, width: Float, id: String) {
        Column(CssStyle(width = px(width), height = px(40), justifyContent = JustifyContent.CENTER), id = id) {
            Text(primary, textStyle(12f, ink, 20f).copy(verticalAlign = VerticalAlign.CENTER))
            Text(secondary, textStyle(10f, muted, 18f).copy(verticalAlign = VerticalAlign.CENTER))
        }
    }

    private fun tableRowStyle(height: Float, background: Int = Color.TRANSPARENT) = CssStyle(width = percent(100), height = px(height), padding = Edges(5f, 10f), alignItems = AlignItems.CENTER, gap = 8f, background = background, border = Border(1f, line))
    private fun tableHeaderRowStyle() = tableRowStyle(40f, darkHeader).copy(
        padding = Edges(0f, 10f),
        cornerRadii = CornerRadii(10f, 10f, 0f, 0f),
    )
    private fun tableCellStyle(width: Float, color: Int) = textStyle(12f, color, 40f).copy(width = px(width), verticalAlign = VerticalAlign.CENTER)

    private fun ElementBuilder.summaryStat(label: String, value: Any?) {
        Column(CssStyle(width = px(140), height = px(55), alignItems = AlignItems.CENTER)) {
            Text(label, textStyle(14f, muted, 22f).copy(textAlign = TextAlign.CENTER))
            Text(value, textStyle(20f, ink, 28f).copy(textAlign = TextAlign.CENTER))
        }
    }

    private fun ElementBuilder.metric(
        value: Any?,
        label: String,
        width: Float = 72f,
        valueColor: Int = ink,
        id: String? = null,
        valueId: String? = null,
    ) {
        Column(CssStyle(width = px(width), height = px(48), alignItems = AlignItems.CENTER, justifyContent = JustifyContent.CENTER), id = id) {
            Text(value, recordTextStyle(14f, valueColor, 22f).copy(textAlign = TextAlign.CENTER), id = valueId)
            Text(label, recordTextStyle(12f, muted, 18f).copy(textAlign = TextAlign.CENTER))
        }
    }
    private fun ElementBuilder.infusionMetric(match: EternalReturnPlayRender.EternalReturnPlayerMatchData, base: String) {
        Column(CssStyle(width = px(72), height = px(55), alignItems = AlignItems.CENTER, justifyContent = JustifyContent.CENTER), id = "infusions-${match.gameId}") {
            Row(CssStyle(wrap = FlexWrap.WRAP, width = px(60), height = px(38), gap = 2f, justifyContent = JustifyContent.CENTER)) {
                match.infusions.orEmpty().take(3).forEachIndexed { index, infusion ->
                    Column(CssStyle(width = px(18), height = px(18), background = Color.makeRGB(240, 241, 246), borderRadius = 3f), id = "infusion-${match.gameId}-$index") {
                        if (infusion.imageUrl.isNotBlank()) {
                            Image(infusion.imageUrl.resolve(base), CssStyle(width = percent(100), height = percent(100), borderRadius = 3f, objectFit = ObjectFit.COVER))
                        }
                        if (infusion.count > 1) {
                            Text(infusion.count, CssStyle(position = Position.ABSOLUTE, right = 0f, bottom = 0f, width = px(10), height = px(10), fontSize = 8f, fontWeight = 700, color = white, background = Color.makeARGB(190, 0, 0, 0), textAlign = TextAlign.CENTER, verticalAlign = VerticalAlign.CENTER))
                        }
                    }
                }
            }
            Text("灌注", recordTextStyle(12f, muted, 17f).copy(textAlign = TextAlign.CENTER))
        }
    }
    private fun panel(height: Float, width: Float) = CssStyle(width = px(width), height = px(height), background = white, border = Border(1f, line), borderRadius = 10f, gap = 0f)
    private fun textStyle(size: Float, color: Int, height: Float) = CssStyle(width = percent(100), height = px(height), fontSize = size, color = color)
    private fun recordTextStyle(size: Float, color: Int, height: Float) = textStyle(size, color, height).copy(verticalAlign = VerticalAlign.CENTER)
    private fun chipStyle() = textStyle(12f, white, 24f).copy(width = px(70), border = Border(2f, white), borderRadius = 15f, textAlign = TextAlign.CENTER,verticalAlign = VerticalAlign.CENTER, fontWeight = 800)
    private fun rankPanelHeight(data: EternalReturnPlayRender) = if (data.mmrStats == null) 347f else 587f
    private fun rankChip(rank: Int) = textStyle(12f, if (rank > 3 && rank != 99) muted else white, 24f).copy(width = px(24), background = when (rank) { 1 -> Color.makeRGB(17,178,136); 2,3 -> Color.makeRGB(32,122,199); 99 -> Color.makeRGB(71,84,130); else -> Color.makeRGB(214,214,214) }, borderRadius = 3f, textAlign = TextAlign.CENTER, verticalAlign = VerticalAlign.CENTER)
    private fun String?.resolve(base: String): String? = this?.takeIf(String::isNotBlank)?.let { if (it.startsWith("http://") || it.startsWith("https://")) it else base.trimEnd('/') + "/" + it.trimStart('/') }
}
