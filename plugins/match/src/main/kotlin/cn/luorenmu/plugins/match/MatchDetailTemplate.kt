package cn.luorenmu.plugins.match

import cn.luorenmu.nutdraw.css.*
import cn.luorenmu.nutdraw.dom.ElementBuilder
import cn.luorenmu.nutdraw.dom.document
import cn.luorenmu.nutdraw.render.CjkFontResolver
import cn.luorenmu.nutdraw.template.ImageTemplate
import cn.luorenmu.nutdraw.template.TemplateDocument
import org.jetbrains.skia.Color
import java.util.Locale

/** Fixed-size bot image, not a fake interactive browser page. */
class MatchDetailTemplate : ImageTemplate<MatchDetailData> {
    private val ink = Color.makeRGB(32, 38, 49)
    private val focusedNameColor = Color.makeRGB(220, 38, 38)
    private val muted = Color.makeRGB(99, 107, 119)
    private val line = Color.makeRGB(224, 227, 232)
    private val pale = Color.makeRGB(248, 249, 251)
    private val accent = Color.makeRGB(138, 98, 8)
    private val selected = Color.makeRGB(255, 249, 233)
    private val teamColors = listOf(
        Color.makeRGB(48, 104, 174), Color.makeRGB(157, 76, 137),
        Color.makeRGB(37, 126, 113), Color.makeRGB(169, 102, 39),
        Color.makeRGB(111, 86, 173), Color.makeRGB(175, 69, 80),
        Color.makeRGB(66, 117, 145), Color.makeRGB(111, 123, 42),
    )
    private val fonts = CjkFontResolver()

    override fun build(data: MatchDetailData): TemplateDocument {
        val teams = data.teams.sortedWith(compareBy<MatchTeam> { it.rank ?: Int.MAX_VALUE }.thenBy { it.number })
        val rows = teams.chunked(2)
        val rowHeights = rows.map { pair -> cardHeight(pair.maxOf { it.players.size }) }
        val gridHeight = rowHeights.sum() + (rows.size - 1).coerceAtLeast(0) * 20
        val height = 64 + 120 + 92 + gridHeight + (if (teams.isEmpty()) 160 else 0) +
            (if (data.designSample) 36 else 0) +
            (2 + (if (teams.isEmpty()) 1 else 0) + (if (data.designSample) 1 else 0)) * 20
        val players = teams.flatMap { it.players }
        val focused = players.firstOrNull { it.id == data.focusedPlayerId }
        val highestDamage = players.maxByOrNull { it.damage ?: -1 }
        return TemplateDocument(1440, height, document(CssStyle(width = px(1440), height = px(height),
            padding = Edges(32f), gap = 20f, background = Color.makeRGB(245, 246, 248), color = ink), "match-page") {
            Row(CssStyle(width = px(1376), height = px(120), justifyContent = JustifyContent.SPACE_BETWEEN,
                alignItems = AlignItems.CENTER), "match-header") {
                Column(CssStyle(width = px(790), height = px(106), gap = 9f)) {
                    Text("对局详情", type(34f, 46f, weight = 800), "match-title")
                    Text("${data.server.ifBlank { "未知服务器" }} · ${data.gameId}   ${data.mode}   版本 ${data.version}", type(18f, 27f), "match-meta")
                    Text("${data.startedAt}   /   对局时长 ${time(data.durationSeconds)}", type(16f, 24f, muted), "match-time")
                }
                Column(CssStyle(width = px(380), height = px(96), alignItems = AlignItems.END, gap = 12f)) {
                    Text(if (data.designSample) "设计样例 · 非实时战绩" else "全场结算详情",
                        type(17f, 34f, accent).copy(width = px(260), background = selected, borderRadius = 5f,
                            textAlign = TextAlign.CENTER), "sample-label")
                    Text("${players.size} 位玩家  /  ${teams.size} 支队伍", type(18f, 28f, muted).copy(textAlign = TextAlign.END))
                }
            }
            Row(CssStyle(width = px(1376), height = px(92), padding = Edges(14f, 24f),
                background = Color.WHITE, border = Border(1f, line), borderRadius = 8f), "match-overview") {
                overview("焦点玩家", fit(focused?.nickname ?: "全场视角", 288f, 24f), "focus", 330f, if (focused != null) focusedNameColor else ink)
                overview("焦点游玩时间", time(focused?.survivalSeconds), "survival", 270f)
                overview("最高对玩家伤害", format(highestDamage?.damage), "top-damage", 330f)
                overview("伤害最高玩家", fit(highestDamage?.nickname ?: "暂无", 288f, 24f), "top-player", 350f, if (focused != null && highestDamage?.id == focused.id) focusedNameColor else ink)
            }
            Column(CssStyle(width = px(1376), height = px(gridHeight), gap = 20f), "team-grid") {
                rows.forEachIndexed { index, pair ->
                    Row(CssStyle(width = px(1376), height = px(rowHeights[index]), gap = 20f), "team-row-$index") {
                        pair.forEach { team -> teamCard(team, data.focusedPlayerId, rowHeights[index]) }
                    }
                }
            }
            if (teams.isEmpty()) Text("暂无可用的对局结算记录", type(22f, 160f, muted).copy(width = px(1376), background = Color.WHITE,
                textAlign = TextAlign.CENTER, verticalAlign = VerticalAlign.CENTER), "empty-state")
            if (data.designSample) Text("设计预览：头像与装备为本地资源，玩家及数值为样例；查询接口尚未接入。",
                type(16f, 36f, muted).copy(width = px(1376)), "data-note")
        })
    }

    private fun cardHeight(count: Int) = 24 + 40 + 28 + count * 88

    private fun ElementBuilder.overview(label: String, value: String, id: String, width: Float, valueColor: Int = ink) {
        Column(CssStyle(width = px(width), height = px(62), gap = 6f)) {
            Text(label, type(15f, 22f, muted), "overview-label-$id")
            Text(value, type(24f, 34f, valueColor, weight = 700), "overview-$id")
        }
    }

    private fun ElementBuilder.teamCard(team: MatchTeam, focus: Long?, height: Int) {
        val containsFocus = team.players.any { it.id == focus }
        val teamColor = teamColors[Math.floorMod(team.number - 1, teamColors.size)]
        Column(CssStyle(width = px(678), height = px(height), padding = Edges(12f, 16f),
            background = Color.WHITE, border = Border(1f, if (containsFocus) accent else line), borderRadius = 8f), "team-${team.number}") {
            Column(CssStyle(position = Position.ABSOLUTE, left = -12f, top = 8f,
                width = px(4), height = px(28), background = teamColor, borderRadius = 2f), "team-color-${team.number}") {}
            Row(CssStyle(width = px(646), height = px(40), alignItems = AlignItems.CENTER, justifyContent = JustifyContent.SPACE_BETWEEN)) {
                Text("${team.rank?.let { "#$it" } ?: "暂无排名"}   队伍 ${team.number}", type(20f, 30f,
                    teamColor, 700).copy(width = px(350)), "team-title-${team.number}")
                Text("TK ${format(team.kills)}${if (containsFocus) "   焦点队伍" else ""}",
                    type(16f, 28f, if (containsFocus) accent else muted).copy(width = px(280), textAlign = TextAlign.END), "team-summary-${team.number}")
            }
            Row(CssStyle(width = px(646), height = px(28), background = pale, alignItems = AlignItems.CENTER)) {
                Text("玩家 / 最终装备", type(14f, 24f, muted).copy(width = px(240)))
                Text("K / D / A · RP", type(14f, 24f, muted).copy(width = px(116), textAlign = TextAlign.CENTER))
                Text("对玩家伤害", type(14f, 24f, muted).copy(width = px(145), textAlign = TextAlign.END))
                Text("承受伤害", type(14f, 24f, muted).copy(width = px(145), textAlign = TextAlign.END))
            }
            team.players.forEach { player ->
                Column(CssStyle(width = px(646), height = px(88), padding = Edges(5f, 0f), gap = 4f,
                    background = if (player.id == focus) selected else Color.WHITE), "player-${player.id}") {
                    Row(CssStyle(width = px(646), height = px(48), alignItems = AlignItems.CENTER)) {
                        Row(CssStyle(width = px(240), height = px(48), alignItems = AlignItems.CENTER, gap = 10f)) {
                            Column(CssStyle(width = px(42), height = px(42))) {
                                if (player.portrait.isNullOrBlank()) Text(player.character.take(1), type(20f, 42f, muted).copy(
                                    width = px(42), background = pale, borderRadius = 6f, textAlign = TextAlign.CENTER))
                                else Image(player.portrait, CssStyle(width = px(42), height = px(42), borderRadius = 6f))
                                if (!player.weaponIcon.isNullOrBlank()) Column(CssStyle(width = px(20), height = px(20),
                                    position = Position.ABSOLUTE, right = 0f, bottom = 0f, padding = Edges(2f),
                                    background = ink, borderRadius = 4f), "weapon-${player.id}") {
                                    Image(player.weaponIcon, CssStyle(width = px(16), height = px(16), objectFit = ObjectFit.CONTAIN))
                                }
                            }
                            Column(CssStyle(width = px(184), height = px(48))) {
                                Text(fit(player.nickname, 180f, 18f), type(18f, 25f, if (player.id == focus) focusedNameColor else ink, weight = 700).copy(width = px(184)), "nickname-${player.id}")
                                Text(fit("${player.character} · ${time(player.survivalSeconds)}", 184f, 15f), type(15f, 23f, muted).copy(width = px(184)))
                            }
                        }
                        Column(CssStyle(width = px(116), height = px(48))) {
                            Text("${format(player.kills)} / ${format(player.deaths)} / ${format(player.assists)}",
                                type(20f, 27f).copy(width = px(116), textAlign = TextAlign.CENTER), "kda-${player.id}")
                            Row(CssStyle(width = px(116), height = px(21), gap = 4f,
                                justifyContent = JustifyContent.CENTER, alignItems = AlignItems.CENTER)) {
                                val total = player.rp?.let { "RP $it" } ?: "RP 暂无"
                                val totalWidth = fonts.runs(total, 14f, 400).sumOf { it.width.toDouble() }.toFloat()
                                val rpColor = when {
                                    player.rp == null -> muted
                                    (player.rpChange ?: 0) > 0 -> Color.makeRGB(25, 128, 81)
                                    (player.rpChange ?: 0) < 0 -> Color.makeRGB(194, 58, 58)
                                    else -> muted
                                }
                                Text(total, type(14f, 21f, rpColor).copy(width = px(totalWidth)), "rp-${player.id}")
                                if (player.rp != null) player.rpChange?.let { change ->
                                    val delta = "${if (change >= 0) "+" else ""}$change"
                                    val deltaWidth = fonts.runs(delta, 14f, 700).sumOf { it.width.toDouble() }.toFloat()
                                    Text(delta, type(14f, 21f, rpColor, 700).copy(width = px(deltaWidth)), "rp-change-${player.id}")
                                }
                            }
                        }
                        damageCell(player.damage, "damage-${player.id}", "治疗 ${format(player.healing)}")
                        damageCell(player.taken, "taken-${player.id}", "视野 ${format(player.vision)} · 荣誉 ${format(player.credits)}")
                    }
                    Row(CssStyle(width = px(646), height = px(26), alignItems = AlignItems.CENTER)) {
                        Row(CssStyle(width = px(240), height = px(24), padding = Edges(left = 52f), gap = 4f), "equipment-${player.id}") {
                            repeat(5) { slot ->
                                val image = player.equipment.getOrNull(slot)
                                if (image.isNullOrBlank()) Text("", CssStyle(width = px(24), height = px(24), background = pale,
                                    border = Border(1f, line), borderRadius = 3f))
                                else Column(CssStyle(width = px(24), height = px(24),
                                    backgroundImage = player.equipmentBackgrounds.getOrNull(slot), background = pale,
                                    borderRadius = 3f), "equipment-slot-${player.id}-$slot") {
                                    Image(image, CssStyle(width = px(24), height = px(24), objectFit = ObjectFit.CONTAIN))
                                }
                            }
                        }
                        Row(CssStyle(width = px(116), height = px(24), gap = 4f, alignItems = AlignItems.CENTER), "tier-${player.id}") {
                            if (!player.tierIcon.isNullOrBlank()) Image(player.tierIcon,
                                CssStyle(width = px(22), height = px(22), objectFit = ObjectFit.CONTAIN), "tier-icon-${player.id}")
                            val tierWidth = if (player.tierIcon.isNullOrBlank()) 116f else 90f
                            Text(fit(player.tierName?.takeIf { it.isNotBlank() } ?: "段位未知", tierWidth, 14f),
                                type(14f, 24f, muted).copy(width = px(tierWidth)), "tier-name-${player.id}")
                        }
                        Row(CssStyle(width = px(290), height = px(24)), "skills-${player.id}") {
                            listOf(Triple("trait", "天赋", player.traitSkillIcon), Triple("tactical", "战术", player.tacticalSkillIcon)).forEach { (kind, label, icon) ->
                                Row(CssStyle(width = px(145), height = px(24), gap = 8f,
                                    justifyContent = JustifyContent.END, alignItems = AlignItems.CENTER), "$kind-group-${player.id}") {
                                    Text(label, type(14f, 24f, muted).copy(width = px(30)), "$kind-label-${player.id}")
                                    if (icon.isNullOrBlank()) Text("—", type(14f, 24f, muted).copy(width = px(24),
                                        background = pale, borderRadius = 4f, textAlign = TextAlign.CENTER), "$kind-skill-${player.id}")
                                    else Image(icon, CssStyle(width = px(24), height = px(24), objectFit = ObjectFit.CONTAIN,
                                        background = pale, borderRadius = 4f), "$kind-skill-${player.id}")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun ElementBuilder.damageCell(value: Long?, id: String, support: String) {
        Column(CssStyle(width = px(145), height = px(48), alignItems = AlignItems.END), "$id-cell") {
            Text(format(value), type(22f, 29f, weight = 700).copy(width = px(145), textAlign = TextAlign.END), id)
            Text(support, type(14f, 19f, muted).copy(width = px(145), textAlign = TextAlign.END), "$id-support")
        }
    }

    private fun fit(value: String, width: Float, size: Float): String {
        fun measured(text: String) = fonts.runs(text, size, 700).sumOf { it.width.toDouble() }.toFloat()
        if (measured(value) <= width) return value
        val codePoints = value.codePoints().toArray()
        var end = codePoints.size
        while (end > 0) {
            val result = String(codePoints, 0, --end) + "…"
            if (measured(result) <= width) return result
        }
        return "…"
    }

    private fun format(value: Number?) = value?.let { String.format(Locale.ROOT, "%,d", it.toLong()) } ?: "暂无"
    private fun time(seconds: Long?) = seconds?.takeIf { it >= 0 }?.let { "%d:%02d".format(Locale.ROOT, it / 60, it % 60) } ?: "暂无"
    private fun type(size: Float, height: Float, color: Int = ink, weight: Int = 400) = CssStyle(
        fontSize = size, lineHeight = height / size, height = px(height), color = color, fontWeight = weight,
        verticalAlign = VerticalAlign.CENTER)
}
