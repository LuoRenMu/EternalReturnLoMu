package cn.luorenmu.plugins.character

import cn.luorenmu.nutdraw.css.*
import cn.luorenmu.nutdraw.dom.ElementBuilder
import cn.luorenmu.nutdraw.dom.document
import cn.luorenmu.nutdraw.template.ImageTemplate
import cn.luorenmu.nutdraw.template.TemplateDocument
import cn.luorenmu.service.entity.CharacterDetail
import org.jetbrains.skia.Color
import java.util.Locale

/** White-and-rose tactical report: flat editorial sections, paired learning panels, real assets and readable type.
 * Portraits use 12px corners, small asset tiles 6px and surfaces 8px; no decorative elevation.
 */
class CharacterDetailTemplate(private val equipmentOnly: Boolean = false) : ImageTemplate<CharacterDetail> {
    private val ink = Color.makeRGB(48, 38, 45)
    private val muted = Color.makeRGB(98, 81, 91)
    private val accent = Color.makeRGB(170, 48, 94)
    private val line = Color.makeRGB(238, 211, 223)
    private val positive = Color.makeRGB(21, 115, 83)
    private val negative = Color.makeRGB(190, 48, 57)

    override fun build(data: CharacterDetail): TemplateDocument {
        val analysis = data.analysis
        val weapon = analysis?.weapons?.firstOrNull()
        if (analysis == null || weapon == null) return TemplateDocument(1000, 220,
            document(CssStyle(width = px(1000), height = px(220), padding = Edges(32f), background = Color.WHITE)) {
                text(data.name, type(38, 60, weight = 800))
                text("当前模式或段位暂无数据，请换一个筛选条件再试。", type(22, 60, muted))
            })
        val base = data.httpServer
        val others = if (equipmentOnly) emptyList() else analysis.weapons.drop(1).take(4)
        val skills = if (equipmentOnly) emptyList() else weapon.skillBuilds.take(2)
        val tactics = if (equipmentOnly) emptyList() else weapon.tacticals.take(4)
        val augments = if (equipmentOnly) emptyList() else weapon.augments.take(4)
        val builds = weapon.itemBuilds.take(4)
        val equipment = weapon.topEquipments.groupBy { it.slot }.entries.take(5)
        val infusions = if (equipmentOnly) emptyList() else weapon.infusions.take(10)
        val players = if (equipmentOnly) emptyList() else analysis.topPlayers.take(8)
        val pairedLearning = skills.isNotEmpty() && tactics.isNotEmpty()
        val learningHeight = if (pairedLearning) maxOf(64 + skills.size * 110,
            64 + ((tactics.size + 1) / 2) * 104 + ((tactics.size - 1) / 2) * 12) else 0
        val heights = listOf(
            342,
            if (others.isNotEmpty()) 64 + others.size * 56 else 0,
            if (pairedLearning) learningHeight else if (skills.isNotEmpty()) 64 + skills.size * 110 else 0,
            if (!pairedLearning && tactics.isNotEmpty()) 184 else 0,
            if (augments.isNotEmpty()) 64 + augments.size * 126 else 0,
            if (builds.isNotEmpty()) 64 + builds.size * 156 else 0,
            if (equipment.isNotEmpty()) 64 + equipment.size * 172 else 0,
            if (infusions.isNotEmpty()) 64 + ((infusions.size + 4) / 5) * 112 else 0,
            if (players.isNotEmpty()) 64 + ((players.size + 3) / 4) * 82 else 0,
        ).filter { it > 0 }
        val height = 64 + heights.sum() + (heights.size - 1) * 24
        return TemplateDocument(1000, height, document(
            CssStyle(width = px(1000), height = px(height), padding = Edges(32f), gap = 24f, background = Color.WHITE, color = ink),
            id = "character-detail",
        ) {
            element(column(342), id = "detail-hero") {
                element(row(180, 24)) {
                    image(data.imageUrl.resolve(base), CssStyle(width = px(180), height = px(180), objectFit = ObjectFit.COVER, background = Color.TRANSPARENT, borderRadius = 12f), id = "detail-portrait")
                    element(CssStyle(flexGrow = 1f, height = px(180), gap = 6f)) {
                        element(row(52, 12)) {
                            characterTierIcon(characterTierIconUrl(analysis.characterTier).resolve(base), 28f, "detail-tier-icon")
                            text(data.name, type(38, 52, weight = 800).copy(width = CssSize.Auto, flexGrow = 1f), id = "detail-character-name")
                            text(characterRankingLabel(weapon.rank, weapon.rankSize),
                                type(28, 52, accent, 800).copy(width = px(210), textAlign = TextAlign.END),
                                id = "detail-character-ranking")
                        }
                        text(listOf(data.title, characterRoleLabels(data.archetypes)).filter(String::isNotBlank).joinToString(" · "), type(20, 30, muted), id = "detail-character-role")
                        text("${analysis.matchingModeLabel} · ${analysis.teamModeLabel} · ${analysis.tierLabel}", type(20, 32, accent, 700))
                        text("版本 ${analysis.patchLabel} · ${analysis.updatedLabel}", type(16, 28, muted))
                    }
                }
                element(row(56, 12).copy(background = Color.TRANSPARENT, padding = Edges(10f, 12f), borderRadius = 8f), id = "main-weapon-strip") {
                    if (weapon.iconUrl.isNotBlank()) element(CssStyle(width = px(32), height = px(32),
                        background = ink, borderRadius = 6f, padding = Edges(4f))) {
                        image(weapon.iconUrl.resolve(base), CssStyle(width = percent(100), height = percent(100), objectFit = ObjectFit.CONTAIN))
                    }
                    characterTierIcon(characterTierIconUrl(weapon.tier).resolve(base), 20f, "main-weapon-tier-icon")
                    text(weapon.weapon, type(22, 34, weight = 800).copy(width = px(140)), id = "main-weapon-name")
                    text("评分 ${decimal(weapon.tierScore)}", type(18, 34).copy(width = px(132)))
                    text("主武器排名 ${characterRankingLabel(weapon.rank, weapon.rankSize)}", type(18, 34, accent, 700).copy(width = px(290)), id = "main-weapon-ranking")
                    text("RP ${signed(weapon.rpChange)}", type(20, 34, rpColor(weapon.rpChange), 700).copy(width = CssSize.Auto, flexGrow = 1f, textAlign = TextAlign.END), id = "main-weapon-rp")
                }
                element(row(106, 12)) {
                    metric("胜率", pct(weapon.winRate), highlighted = true)
                    metric("登场率", pct(weapon.pickRate))
                    metric("前三率", pct(weapon.top3Rate))
                    metric("平均名次", decimal(weapon.avgRank))
                    metric("平均击杀", decimal(weapon.avgKills))
                    metric("对局数", weapon.games.toString())
                }
            }
            if (others.isNotEmpty()) section("其他武器", 64 + others.size * 56, "other-weapons-panel") {
                others.forEachIndexed { i, w ->
                    element(row(56, 12).copy(background = Color.TRANSPARENT, padding = Edges(10f, 12f)), id = "other-weapon-$i") {
                        characterTierIcon(characterTierIconUrl(w.tier).resolve(base), 22f, "other-weapon-tier-icon-$i")
                        text(w.weapon, type(20, 34, weight = 700).copy(width = px(150)), id = "other-weapon-name-$i")
                        text("评分 ${decimal(w.tierScore)}", type(18, 34).copy(width = px(150)))
                        text("登场 ${pct(w.pickRate)}", type(18, 34).copy(width = px(180)))
                        text("胜率 ${pct(w.winRate)}", type(18, 34, ink, 700).copy(width = px(150)))
                        text("RP ${signed(w.rpChange)}", type(18, 34, rpColor(w.rpChange), 700).copy(width = CssSize.Auto, flexGrow = 1f, textAlign = TextAlign.END), id = "other-weapon-rp-$i")
                    }
                }
            }
            if (pairedLearning) {
                element(row(learningHeight, 16).copy(alignItems = AlignItems.START), id = "learning-and-tactics") {
                    skillSection(skills, weapon, base, learningHeight, 536f)
                    tacticalSection(tactics, base, learningHeight, 384f, paired = true)
                }
            } else {
                if (skills.isNotEmpty()) skillSection(skills, weapon, base, 64 + skills.size * 110)
                if (tactics.isNotEmpty()) tacticalSection(tactics, base, 184)
            }
            if (augments.isNotEmpty()) section("潜能搭配", 64 + augments.size * 126, "augment-panel", "augment-header") {
                augments.forEachIndexed { i, augment ->
                    element(row(126, 16).copy(padding = Edges(8f, 12f), background = Color.TRANSPARENT)) {
                        element(CssStyle(width = px(210), height = px(108), gap = 4f)) {
                            element(CssStyle(position = Position.ABSOLUTE, right = 0f, top = 8f,
                                width = px(1), height = px(92), background = line))
                            element(row(44, 10)) {
                                image(augment.core.iconUrl.resolve(base), CssStyle(width = px(40), height = px(40), objectFit = ObjectFit.CONTAIN))
                                text(augment.core.name, type(20, 42, weight = 700).copy(width = CssSize.Auto, flexGrow = 1f))
                            }
                            text("方案 ${i + 1}", type(18, 26, accent, 700))
                            text("登场 ${pct(augment.core.pickRate)}", type(18, 26, muted))
                        }
                        element(row(108, 8).copy(width = CssSize.Auto, flexGrow = 1f)) {
                            augment.subs.take(6).forEachIndexed { j, sub ->
                                element(CssStyle(width = px(105), height = px(108), alignItems = AlignItems.CENTER, gap = 6f)) {
                                    image(sub.iconUrl.resolve(base), CssStyle(width = px(44), height = px(44), objectFit = ObjectFit.CONTAIN))
                                    text(sub.name, type(16, 54, weight = 600).copy(textAlign = TextAlign.CENTER), id = "augment-sub-name-$i-$j")
                                }
                            }
                        }
                    }
                }
            }
            if (builds.isNotEmpty()) section("成装方案", 64 + builds.size * 156, "loadout-panel", "loadout-header") {
                builds.forEachIndexed { i, build ->
                    element(column(156).copy(padding = Edges(8f, 12f), background = Color.TRANSPARENT), id = "loadout-row-$i") {
                        element(row(36, 16)) {
                            text("方案 ${i + 1}", type(22, 34, accent, 700).copy(width = px(250)))
                            text("登场 ${pct(build.pickRate)}   胜率 ${pct(build.winRate)}", type(18, 34).copy(width = CssSize.Auto, flexGrow = 1f, textAlign = TextAlign.END))
                        }
                        element(row(104, 12)) {
                            build.items.take(5).forEach { item -> itemLabel(item.name, item.iconUrl, item.bgUrl, base) }
                        }
                    }
                }
            }
            if (equipment.isNotEmpty()) section("单件装备选择", 64 + equipment.size * 172, "equipment-panel", "equipment-header") {
                equipment.forEachIndexed { i, (slot, items) ->
                    element(column(172), id = "equipment-slot-$i") {
                        text(slot, type(20, 32, accent, 700))
                        element(row(140, 12)) {
                            items.take(5).forEachIndexed { j, item ->
                                element(CssStyle(width = px(177), height = px(132), padding = Edges(8f), background = Color.TRANSPARENT, borderRadius = 8f), id = "equipment-item-$i-$j") {
                                    element(row(68, 8)) {
                                        itemIcon(item.iconUrl, item.bgUrl, base, 52)
                                        text(item.name, type(16, 66, weight = 600).copy(width = CssSize.Auto, flexGrow = 1f), id = "equipment-name-$i-$j")
                                    }
                                    text("选择 ${pct(item.pickRate)}", type(18, 24, muted), id = "equipment-pick-rate-$i-$j")
                                    text("胜率 ${pct(item.winRate)}", type(18, 24, accent, 700), id = "equipment-win-rate-$i-$j")
                                }
                            }
                        }
                    }
                }
            }
            if (infusions.isNotEmpty()) section("灌注选择", 64 + ((infusions.size + 4) / 5) * 112, "infusion-panel") {
                infusions.chunked(5).forEach { picks ->
                    element(row(112, 12)) {
                        picks.forEach { pick ->
                            element(CssStyle(width = px(177), height = px(112))) {
                                itemLabel(pick.name, pick.iconUrl, pick.bgUrl, base, 82)
                                text("选择 ${pct(pick.pickRate)}", type(18, 28, accent))
                            }
                        }
                    }
                }
            }
            if (players.isNotEmpty()) section("高分玩家", 64 + ((players.size + 3) / 4) * 82, "players-panel") {
                players.chunked(4).forEach { group ->
                    element(row(82, 12)) {
                        group.forEach { player ->
                            element(CssStyle(direction = FlexDirection.ROW, width = px(225), height = px(74), gap = 10f, padding = Edges(8f), background = Color.TRANSPARENT, alignItems = AlignItems.CENTER)) {
                                image(player.tierIconUrl.resolve(base), CssStyle(width = px(40), height = px(40), objectFit = ObjectFit.CONTAIN))
                                element(CssStyle(flexGrow = 1f, height = px(58))) {
                                    text(player.name, type(16, 32, weight = 600))
                                    text("${player.mmr} 分", type(18, 26, muted))
                                }
                            }
                        }
                    }
                }
            }
        })
    }

    private fun ElementBuilder.skillSection(skills: List<CharacterDetail.SkillBuild>, weapon: CharacterDetail.WeaponBuild,
                                            base: String, height: Int, width: Float? = null) {
        section("技能学习 · 优先升满顺序", height, "skill-panel", width = width) {
            skills.forEachIndexed { i, plan ->
                element(column(110).copy(padding = Edges(8f), background = Color.TRANSPARENT,
                    justifyContent = JustifyContent.CENTER, borderRadius = 8f), id = "skill-plan-$i") {
                    element(row(64, 12).copy(fontSize = 22f), id = "skill-priority-$i") {
                        text("方案 ${i + 1}", type(22, 38, accent, 700).copy(width = px(76)))
                        element(row(64, 8).copy(width = CssSize.Auto, flexGrow = 1f)) {
                            plan.priority.forEachIndexed { index, slot ->
                                if (index > 0) text("→", type(22, 32, muted).copy(width = px(22)))
                                element(CssStyle(width = px(44), height = px(64), alignItems = AlignItems.CENTER),
                                    id = "skill-slot-$i-$index") {
                                    val skill = weapon.skillBySlot[slot] ?: weapon.skills.firstOrNull { it.slot == slot }
                                    val icon = skill?.iconUrl.resolve(base)
                                    element(CssStyle(width = px(40), height = px(40), background = line, borderRadius = 6f)) {
                                        if (icon != null) image(icon, CssStyle(width = percent(100), height = percent(100),
                                            objectFit = ObjectFit.CONTAIN, borderRadius = 6f), id = "skill-icon-$i-$index")
                                    }
                                    text(slot, type(22, 24, accent, 700).copy(textAlign = TextAlign.CENTER), id = "skill-key-$i-$index")
                                }
                            }
                        }
                    }
                    text("登场 ${pct(plan.pickRate)}   胜率 ${pct(plan.winRate)}", type(18, 30, muted), id = "skill-statistics-$i")
                }
            }
        }
    }

    private fun ElementBuilder.tacticalSection(picks: List<CharacterDetail.Pick>, base: String, height: Int,
                                               width: Float? = null, paired: Boolean = false) {
        section("战术技能", height, "tactical-panel", "tactical-header", width) {
            val groups = if (paired) picks.chunked(2) else listOf(picks)
            element(column(height - 64).copy(gap = 12f)) {
                groups.forEachIndexed { rowIndex, group ->
                    element(row(if (paired) 104 else 120, 12)) {
                        group.forEachIndexed { itemIndex, pick ->
                            element(CssStyle(width = px(if (paired) 186 else 225), height = px(if (paired) 104 else 120),
                                padding = Edges(if (paired) 8f else 12f), background = Color.TRANSPARENT, borderRadius = 8f),
                                id = "tactical-choice-${rowIndex * (if (paired) 2 else 4) + itemIndex}") {
                                element(row(if (paired) 36 else 48, 8)) {
                                    image(pick.iconUrl.resolve(base), CssStyle(width = px(if (paired) 32 else 44),
                                        height = px(if (paired) 32 else 44), objectFit = ObjectFit.CONTAIN))
                                    text(pick.name, type(20, if (paired) 36 else 44, weight = 700).copy(width = CssSize.Auto, flexGrow = 1f))
                                }
                                text("登场 ${pct(pick.pickRate)}", type(18, 26, muted))
                                text("胜率 ${pct(pick.winRate)}", type(18, 26, accent, 700))
                            }
                        }
                    }
                }
            }
        }
    }

    private fun ElementBuilder.section(title: String, height: Int, id: String, headerId: String = "$id-header",
                                       width: Float? = null, content: ElementBuilder.() -> Unit) {
        element(column(height).copy(width = width?.let { px(it) } ?: percent(100)), id = id) {
            element(row(64, 12), id = headerId) {
                element(CssStyle(width = px(3), height = px(24), background = accent, borderRadius = 1f))
                text(title, type(26, 48, weight = 800).copy(width = CssSize.Auto, flexGrow = 1f))
                element(CssStyle(position = Position.ABSOLUTE, left = 0f, bottom = 8f, width = percent(100),
                    height = px(1), background = line), id = "$headerId-divider")
            }
            content()
        }
    }

    private fun ElementBuilder.metric(label: String, value: String, highlighted: Boolean = false) {
        element(CssStyle(width = px(146), height = px(106), justifyContent = JustifyContent.CENTER)) {
            element(CssStyle(position = Position.ABSOLUTE, right = 0f, top = 30f, width = px(1), height = px(46), background = line))
            text(value, type(30, 46, if (highlighted) accent else ink, 800))
            text(label, type(18, 30, muted))
        }
    }

    private fun ElementBuilder.itemLabel(name: String, icon: String?, bg: String?, base: String, height: Int = 104) {
        if (height >= 104) {
            element(CssStyle(width = px(172), height = px(height), gap = 4f, alignItems = AlignItems.CENTER)) {
                itemIcon(icon, bg, base, 52)
                text(name, type(18, 48, weight = 600).copy(textAlign = TextAlign.CENTER))
            }
        } else {
            element(CssStyle(direction = FlexDirection.ROW, width = px(172), height = px(height), gap = 8f, alignItems = AlignItems.CENTER)) {
                itemIcon(icon, bg, base, 48)
                text(name, type(18, 80, weight = 600).copy(width = CssSize.Auto, flexGrow = 1f))
            }
        }
    }

    private fun ElementBuilder.itemIcon(icon: String?, bg: String?, base: String, size: Int) {
        element(CssStyle(width = px(size), height = px(size), backgroundImage = bg.resolve(base), borderRadius = 6f)) {
            image(icon.resolve(base), CssStyle(width = percent(100), height = percent(100), objectFit = ObjectFit.CONTAIN))
        }
    }

    private fun column(height: Int) = CssStyle(width = percent(100), height = px(height))
    private fun row(height: Int, gap: Int) = CssStyle(direction = FlexDirection.ROW, width = percent(100), height = px(height), gap = gap.toFloat(), alignItems = AlignItems.CENTER)
    private fun type(size: Int, height: Int, color: Int = ink, weight: Int = 400) = CssStyle(width = percent(100), height = px(height), fontSize = size.toFloat(), fontWeight = weight, color = color, verticalAlign = VerticalAlign.CENTER)
    private fun decimal(value: Double) = String.format(Locale.ROOT, "%.1f", value)
    private fun pct(value: Double) = "${decimal(value)}%"
    private fun rpColor(value: Double) = when { value > 0 -> positive; value < 0 -> negative; else -> muted }
    private fun signed(value: Double) = (if (value > 0) "+" else "") + decimal(value)
    private fun String?.resolve(base: String) = this?.takeIf(String::isNotBlank)?.let { if (it.startsWith("http") || it.startsWith("file:")) it else base.trimEnd('/') + "/" + it.trimStart('/') }
}
