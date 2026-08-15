package cn.luorenmu.plugins.character

import cn.luorenmu.nutdraw.css.*
import cn.luorenmu.nutdraw.dom.ElementBuilder
import cn.luorenmu.nutdraw.dom.document
import cn.luorenmu.nutdraw.template.ImageTemplate
import cn.luorenmu.nutdraw.template.TemplateDocument
import cn.luorenmu.service.entity.CharacterDetail
import org.jetbrains.skia.Color

/** Compact character guide with skill learning plans as the primary visual focus. */
class CharacterDetailTemplate : ImageTemplate<CharacterDetail> {
    private val pageBg = Color.makeRGB(239, 242, 248)
    private val card = Color.WHITE
    private val ink = Color.makeRGB(34, 38, 54)
    private val muted = Color.makeRGB(119, 126, 150)
    private val line = Color.makeRGB(222, 226, 237)
    private val soft = Color.makeRGB(247, 248, 252)
    private val dark = Color.makeRGB(35, 39, 58)
    private val accent = Color.makeRGB(112, 87, 217)
    private val accentSoft = Color.makeRGB(240, 237, 255)
    private val skillAccent = Color.makeRGB(244, 103, 79)
    private val green = Color.makeRGB(24, 161, 108)
    private val red = Color.makeRGB(224, 82, 82)
    private val white = Color.WHITE

    override fun build(data: CharacterDetail): TemplateDocument {
        val analysis = data.analysis
        if (analysis == null || analysis.weapons.isEmpty()) return emptyDocument(data)
        val weapon = analysis.weapons.first()
        val otherWeapons = analysis.weapons.drop(1).take(4)
        val skillPlans = weapon.skillBuilds.take(4)
        val tacticals = weapon.tacticals.take(4)
        val augments = weapon.augments.take(4)
        val itemBuilds = weapon.itemBuilds.take(4)
        val equipmentGroups = weapon.topEquipments.groupBy { it.slot }.entries.take(5)
        val otherWeaponsHeight = if (otherWeapons.isNotEmpty()) 104 else 0
        val skillHeight = if (skillPlans.isNotEmpty()) 58 + 18 + skillPlans.size * 64 else 0
        val guideHeight = if (tacticals.isNotEmpty() || augments.isNotEmpty()) 244 else 0
        val loadoutHeight = if (itemBuilds.isNotEmpty() || equipmentGroups.isNotEmpty()) 322 else 0
        val infusionHeight = if (weapon.infusions.isNotEmpty()) 122 else 0
        val playerHeight = if (analysis.topPlayers.isNotEmpty()) 88 else 0
        val blocks = listOf(otherWeaponsHeight, skillHeight, guideHeight, loadoutHeight, infusionHeight, playerHeight).count { it > 0 }
        val height = 28 + 150 + otherWeaponsHeight + skillHeight + guideHeight + loadoutHeight + infusionHeight + playerHeight + (blocks * 16) + 28
        val base = data.httpServer

        return TemplateDocument(
            1180,
            height,
            document(CssStyle(width = px(1180), height = px(height), padding = Edges(28f), gap = 16f, background = pageBg, color = ink)) {
                hero(data, analysis, weapon, base)

                if (otherWeapons.isNotEmpty()) {
                    otherWeaponsPanel(otherWeapons, base)
                }

                if (skillPlans.isNotEmpty()) {
                    element(cardStyle(skillHeight.toFloat()).copy(border = Border(2f, accent))) {
                        sectionHeader("推荐技能学习")
                        element(CssStyle(width = percent(100), flexGrow = 1f, padding = Edges(10f, 14f, 14f, 14f), gap = 8f)) {
                            skillPlans.forEachIndexed { index, plan -> skillPlan(index, plan, weapon.skillBySlot, base) }
                        }
                    }
                }

                if (guideHeight > 0) {
                    element(CssStyle(direction = FlexDirection.ROW, width = percent(100), height = px(guideHeight), gap = 16f)) {
                        element(cardStyle(guideHeight.toFloat()).copy(width = px(430))) {
                            sectionHeader("推荐战术技能", "tactical-header")
                            element(CssStyle(direction = FlexDirection.ROW, wrap = FlexWrap.WRAP, width = percent(100), flexGrow = 1f, padding = Edges(10f, 14f, 14f, 14f), gap = 8f)) {
                                tacticals.forEachIndexed { index, pick -> tacticalCard(index, pick, base) }
                            }
                        }
                        element(cardStyle(guideHeight.toFloat()).copy(width = px(678)), id = "augment-panel") {
                            sectionHeader("推荐潜能", "augment-header")
                            element(CssStyle(width = percent(100), flexGrow = 1f, padding = Edges(8f, 14f, 14f, 14f), gap = 6f)) {
                                augments.forEachIndexed { index, augment -> augmentRow(index, augment, base) }
                            }
                        }
                    }
                }

                if (loadoutHeight > 0) {
                    element(CssStyle(direction = FlexDirection.ROW, width = percent(100), height = px(loadoutHeight), gap = 16f)) {
                        element(cardStyle(loadoutHeight.toFloat()).copy(width = px(710))) {
                            sectionHeader("最终装备方案", "loadout-header")
                            element(CssStyle(width = percent(100), flexGrow = 1f, padding = Edges(8f, 14f, 14f, 14f), gap = 6f)) {
                                itemBuilds.forEachIndexed { index, build -> loadoutRow(index, build, base) }
                            }
                        }
                        element(cardStyle(loadoutHeight.toFloat()).copy(width = px(398)), id = "equipment-panel") {
                            sectionHeader("物品选择统计", "equipment-header")
                            element(CssStyle(width = percent(100), flexGrow = 1f, padding = Edges(8f, 12f, 12f, 12f), gap = 4f)) {
                                equipmentGroups.forEachIndexed { slotIndex, (slot, equipments) ->
                                    equipmentSlotRow(slotIndex, slot, equipments.take(5), base)
                                }
                            }
                        }
                    }
                }

                if (infusionHeight > 0) {
                    element(cardStyle(infusionHeight.toFloat())) {
                        sectionHeader("灌注选择")
                        element(CssStyle(direction = FlexDirection.ROW, width = percent(100), flexGrow = 1f, padding = Edges(7f, 14f, 12f, 14f), gap = 8f)) {
                            weapon.infusions.take(10).forEach { compactPick(it, base) }
                        }
                    }
                }

                if (playerHeight > 0) {
                    element(cardStyle(playerHeight.toFloat()).copy(direction = FlexDirection.ROW, padding = Edges(12f, 16f), gap = 10f, alignItems = AlignItems.CENTER)) {
                        text("高分玩家", txt(13f, ink, 26f, 700).copy(width = px(76)))
                        analysis.topPlayers.take(8).forEach { player ->
                            element(CssStyle(direction = FlexDirection.ROW, flexGrow = 1f, height = px(46), padding = Edges(6f, 8f), gap = 6f, alignItems = AlignItems.CENTER, background = soft, borderRadius = 9f)) {
                                image(player.tierIconUrl.resolve(base), CssStyle(width = px(28), height = px(28), objectFit = ObjectFit.CONTAIN))
                                element(CssStyle(flexGrow = 1f, height = px(34))) {
                                    text(player.name, txt(10f, ink, 17f, 600))
                                    text(player.mmr, txt(9f, muted, 15f))
                                }
                            }
                        }
                    }
                }
            },
        )
    }

    private fun ElementBuilder.hero(
        data: CharacterDetail,
        analysis: CharacterDetail.CharacterAnalysis,
        weapon: CharacterDetail.WeaponBuild,
        base: String,
    ) {
        element(cardStyle(150f).copy(direction = FlexDirection.ROW, padding = Edges(18f), gap = 16f, background = dark, border = Border())) {
            image(data.imageUrl.resolve(base), CssStyle(width = px(112), height = px(112), borderRadius = 14f, border = Border(2f, Color.makeRGB(77, 82, 108)), objectFit = ObjectFit.COVER))
            element(CssStyle(width = px(275), height = px(112), gap = 4f)) {
                element(CssStyle(direction = FlexDirection.ROW, width = percent(100), height = px(34), gap = 10f, alignItems = AlignItems.CENTER)) {
                    characterTierIcon(characterTierIconUrl(analysis.characterTier).resolve(base), 26f, "detail-tier-icon")
                    text(data.name, txt(27f, white, 34f, 700).copy(width = px(239)), id = "detail-character-name")
                }
                text(data.title, txt(12f, Color.makeRGB(184, 189, 210), 19f))
                element(CssStyle(direction = FlexDirection.ROW, width = percent(100), height = px(24), gap = 5f)) {
                    data.archetypes.filter { it.isNotBlank() && it != "None" }.take(3).forEach { tag ->
                        text(tag, CssStyle(width = px(tag.length * 13 + 18), height = px(22), padding = Edges(2f, 8f), fontSize = 10f, fontWeight = 600, color = Color.makeRGB(215, 208, 255), background = Color.makeRGB(68, 62, 101), borderRadius = 11f, textAlign = TextAlign.CENTER))
                    }
                }
                text("${analysis.matchingModeLabel} · ${analysis.teamModeLabel} · ${analysis.tierLabel}", txt(11f, Color.makeRGB(164, 170, 194), 18f))
                text("版本 ${analysis.patchLabel} · ${analysis.updatedLabel}", txt(10f, Color.makeRGB(132, 140, 169), 16f))
            }
            element(CssStyle(direction = FlexDirection.ROW, width = px(224), height = px(112), padding = Edges(10f), gap = 10f, alignItems = AlignItems.CENTER, background = Color.makeRGB(45, 49, 70), borderRadius = 13f)) {
                image(weapon.iconUrl.resolve(base), CssStyle(width = px(58), height = px(58), padding = Edges(6f), background = Color.makeRGB(16, 18, 27), borderRadius = 12f, objectFit = ObjectFit.CONTAIN))
                element(CssStyle(flexGrow = 1f, height = px(92), gap = 2f, justifyContent = JustifyContent.CENTER)) {
                    element(CssStyle(direction = FlexDirection.ROW, width = percent(100), height = px(24), gap = 5f, alignItems = AlignItems.CENTER)) {
                        characterTierIcon(characterTierIconUrl(weapon.tier).resolve(base), 18f, "main-weapon-tier-icon")
                        text(weapon.weapon, txt(15f, white, 24f, 700).copy(flexGrow = 1f), id = "main-weapon-name")
                    }
                    text("${"%.1f".format(weapon.tierScore)} 分", txt(11f, Color.makeRGB(205, 199, 244), 18f, 600))
                    text("RP变动 ${signed(weapon.rpChange)}", txt(10f, rpColor(weapon.rpChange), 16f, 700), id = "main-weapon-rp")
                    text(
                        "实验体排名 #${weapon.rank} / ${weapon.rankSize}",
                        CssStyle(width = percent(100), height = px(20), padding = Edges(2f, 6f), fontSize = 12f, fontWeight = 700, color = Color.makeRGB(255, 222, 122), background = Color.makeRGB(63, 58, 91), borderRadius = 6f),
                        id = "main-weapon-ranking",
                    )
                }
            }
            element(CssStyle(direction = FlexDirection.ROW, wrap = FlexWrap.WRAP, flexGrow = 1f, height = px(112), gap = 7f)) {
                heroStat("登场率", pct(weapon.pickRate))
                heroStat("胜率", pct(weapon.winRate), green)
                heroStat("TOP3", pct(weapon.top3Rate))
                heroStat("平均名次", "#${"%.1f".format(weapon.avgRank)}")
                heroStat("平均击杀", "%.1f".format(weapon.avgKills))
                heroStat("对局数", weapon.games)
            }
        }
    }

    private fun ElementBuilder.skillPlan(
        index: Int,
        plan: CharacterDetail.SkillBuild,
        skills: Map<String, CharacterDetail.SkillSlot>,
        base: String,
    ) {
        element(CssStyle(direction = FlexDirection.ROW, width = percent(100), height = px(56), padding = Edges(7f, 10f), gap = 10f, alignItems = AlignItems.CENTER, background = if (index == 0) accentSoft else soft, border = Border(1f, if (index == 0) Color.makeRGB(207, 198, 247) else line), borderRadius = 11f)) {
            text("${index + 1}", CssStyle(width = px(34), height = px(34), padding = Edges(6f), fontSize = 14f, fontWeight = 700, color = white, background = if (index == 0) accent else Color.makeRGB(150, 156, 178), borderRadius = 17f, textAlign = TextAlign.CENTER))
            element(CssStyle(width = px(154), height = px(40), justifyContent = JustifyContent.CENTER)) {
                text("升级优先", txt(9f, muted, 14f, 600))
                text(plan.priority.joinToString("  >  "), txt(13f, ink, 21f, 700))
            }
            element(CssStyle(direction = FlexDirection.ROW, flexGrow = 1f, height = px(42), gap = 4f, alignItems = AlignItems.CENTER)) {
                plan.order.take(15).forEachIndexed { _, slot ->
                    val skill = skills[slot]
                    element(CssStyle(width = px(31), height = px(42), alignItems = AlignItems.CENTER)) {
                        image(skill?.iconUrl.resolve(base), CssStyle(width = px(29), height = px(29), borderRadius = 6f, border = Border(1f, line), objectFit = ObjectFit.CONTAIN))
                        text(slot, txt(8f, muted, 11f, 600).copy(textAlign = TextAlign.CENTER))
                    }
                }
            }
            element(CssStyle(width = px(158), height = px(38), alignItems = AlignItems.END, justifyContent = JustifyContent.CENTER)) {
                text("登场 ${pct(plan.pickRate)}", txt(10f, muted, 17f, 600).copy(textAlign = TextAlign.END))
                text("胜率 ${pct(plan.winRate)}", txt(11f, green, 18f, 700).copy(textAlign = TextAlign.END))
            }
        }
    }

    private fun ElementBuilder.tacticalCard(index: Int, pick: CharacterDetail.Pick, base: String) {
        element(CssStyle(direction = FlexDirection.ROW, width = px(195), height = px(66), padding = Edges(8f), gap = 8f, alignItems = AlignItems.CENTER, background = if (index == 0) accentSoft else soft, border = Border(1f, line), borderRadius = 11f)) {
            image(pick.iconUrl.resolve(base), CssStyle(width = px(42), height = px(42), borderRadius = 9f, objectFit = ObjectFit.CONTAIN))
            element(CssStyle(flexGrow = 1f, height = px(44), justifyContent = JustifyContent.CENTER)) {
                text(pick.name, txt(11f, ink, 18f, 700))
                text("登场 ${pct(pick.pickRate)}", txt(9f, muted, 14f))
                text("胜率 ${pct(pick.winRate)}", txt(9f, green, 14f, 600))
            }
        }
    }

    private fun ElementBuilder.augmentRow(index: Int, augment: CharacterDetail.Augment, base: String) {
        element(CssStyle(direction = FlexDirection.ROW, width = percent(100), height = px(38), padding = Edges(4f, 8f), gap = 6f, alignItems = AlignItems.CENTER, background = if (index == 0) accentSoft else soft, borderRadius = 9f)) {
            text("${index + 1}", txt(10f, accent, 22f, 700).copy(width = px(18), textAlign = TextAlign.CENTER))
            image(augment.core.iconUrl.resolve(base), CssStyle(width = px(30), height = px(30), borderRadius = 7f, border = Border(2f, accent), objectFit = ObjectFit.CONTAIN))
            text(augment.core.name, txt(10f, ink, 22f, 700).copy(width = px(96)))
            element(CssStyle(direction = FlexDirection.ROW, flexGrow = 1f, height = px(30), gap = 5f, alignItems = AlignItems.CENTER)) {
                augment.subs.take(6).forEachIndexed { subIndex, sub ->
                    element(CssStyle(width = px(58), height = px(30), alignItems = AlignItems.CENTER)) {
                        image(sub.iconUrl.resolve(base), CssStyle(width = px(19), height = px(19), borderRadius = 5f, objectFit = ObjectFit.CONTAIN))
                        text(sub.name, txt(6f, ink, 10f, 600).copy(width = px(58), textAlign = TextAlign.CENTER), id = "augment-sub-name-$index-$subIndex")
                    }
                }
            }
            text(pct(augment.core.pickRate), txt(9f, muted, 20f, 600).copy(width = px(52), textAlign = TextAlign.END))
        }
    }

    private fun ElementBuilder.loadoutRow(index: Int, build: CharacterDetail.ItemBuild, base: String) {
        element(CssStyle(direction = FlexDirection.ROW, width = percent(100), height = px(48), padding = Edges(5f, 9f), gap = 8f, alignItems = AlignItems.CENTER, background = if (index == 0) Color.makeRGB(255, 244, 241) else soft, borderRadius = 10f)) {
            text("方案 ${index + 1}", txt(10f, if (index == 0) skillAccent else muted, 24f, 700).copy(width = px(52)))
            element(CssStyle(direction = FlexDirection.ROW, flexGrow = 1f, height = px(38), gap = 8f, alignItems = AlignItems.CENTER)) {
                build.items.take(5).forEach { item ->
                    element(CssStyle(direction = FlexDirection.ROW, width = px(88), height = px(38), padding = Edges(3f), gap = 4f, alignItems = AlignItems.CENTER, background = card, border = Border(1f, line), borderRadius = 8f)) {
                        element(CssStyle(width = px(32), height = px(32), padding = Edges(2f), backgroundImage = item.bgUrl.resolve(base), borderRadius = 6f)) {
                            image(item.iconUrl.resolve(base), CssStyle(width = percent(100), height = percent(100), objectFit = ObjectFit.CONTAIN))
                        }
                        text(item.name, txt(8f, ink, 22f, 600).copy(flexGrow = 1f))
                    }
                }
            }
            element(CssStyle(width = px(104), height = px(36), alignItems = AlignItems.END)) {
                text("登场 ${pct(build.pickRate)}", txt(9f, muted, 16f).copy(textAlign = TextAlign.END))
                text("胜率 ${pct(build.winRate)}", txt(10f, green, 17f, 700).copy(textAlign = TextAlign.END))
            }
        }
    }

    private fun ElementBuilder.equipmentSlotRow(
        slotIndex: Int,
        slot: String,
        equipments: List<CharacterDetail.EquipmentSlotPick>,
        base: String,
    ) {
        element(CssStyle(direction = FlexDirection.ROW, width = percent(100), height = px(46), gap = 4f, alignItems = AlignItems.CENTER), id = "equipment-slot-$slotIndex") {
            text(slot, txt(9f, muted, 22f, 600).copy(width = px(32), textAlign = TextAlign.CENTER))
            equipments.forEachIndexed { itemIndex, equipment ->
                element(CssStyle(direction = FlexDirection.ROW, width = px(64), height = px(42), padding = Edges(3f), gap = 2f, alignItems = AlignItems.CENTER, background = soft, border = Border(1f, line), borderRadius = 8f), id = "equipment-item-$slotIndex-$itemIndex") {
                    element(CssStyle(width = px(24), height = px(24), padding = Edges(2f), background = card, backgroundImage = equipment.bgUrl.resolve(base), borderRadius = 6f)) {
                        image(equipment.iconUrl.resolve(base), CssStyle(width = percent(100), height = percent(100), objectFit = ObjectFit.CONTAIN))
                    }
                    element(CssStyle(width = px(32), height = px(34), justifyContent = JustifyContent.CENTER)) {
                        text(equipment.name, txt(6f, ink, 11f, 600).copy(width = px(32), textAlign = TextAlign.CENTER), id = "equipment-name-$slotIndex-$itemIndex")
                        text("选${pct(equipment.pickRate)}", txt(6f, muted, 11f, 600).copy(width = px(32), textAlign = TextAlign.CENTER), id = "equipment-pick-rate-$slotIndex-$itemIndex")
                        text("胜${pct(equipment.winRate)}", txt(6f, green, 11f, 700).copy(width = px(32), textAlign = TextAlign.CENTER), id = "equipment-win-rate-$slotIndex-$itemIndex")
                    }
                }
            }
        }
    }

    private fun ElementBuilder.otherWeaponsPanel(
        weapons: List<CharacterDetail.WeaponBuild>,
        base: String,
    ) {
        element(cardStyle(104f).copy(direction = FlexDirection.ROW, padding = Edges(12f, 14f), gap = 8f, alignItems = AlignItems.CENTER), id = "other-weapons-panel") {
            text("其他武器", txt(12f, ink, 24f, 700).copy(width = px(72), textAlign = TextAlign.CENTER))
            weapons.forEachIndexed { index, weapon ->
                element(CssStyle(direction = FlexDirection.ROW, flexGrow = 1f, height = px(78), padding = Edges(7f), gap = 7f, alignItems = AlignItems.CENTER, background = Color.makeRGB(235, 237, 243), borderRadius = 10f), id = "other-weapon-$index") {
                    characterTierIcon(characterTierIconUrl(weapon.tier).resolve(base), 20f, "other-weapon-tier-icon-$index")
                    element(CssStyle(flexGrow = 1f, height = px(62), justifyContent = JustifyContent.CENTER)) {
                        text(weapon.weapon, txt(11f, ink, 17f, 700), id = "other-weapon-name-$index")
                        text("${"%.1f".format(weapon.tierScore)} 分 · 登场 ${pct(weapon.pickRate)}", txt(8f, muted, 14f, 600))
                        text("胜率 ${pct(weapon.winRate)} · RP ${signed(weapon.rpChange)}", txt(8f, rpColor(weapon.rpChange), 14f, 600), id = "other-weapon-rp-$index")
                    }
                }
            }
        }
    }

    private fun ElementBuilder.compactPick(pick: CharacterDetail.Pick, base: String) {
        element(CssStyle(direction = FlexDirection.ROW, flexGrow = 1f, height = px(48), padding = Edges(5f), gap = 5f, alignItems = AlignItems.CENTER, background = soft, borderRadius = 9f)) {
            image(pick.iconUrl.resolve(base), CssStyle(width = px(34), height = px(34), borderRadius = 7f, objectFit = ObjectFit.CONTAIN))
            element(CssStyle(flexGrow = 1f, height = px(34), justifyContent = JustifyContent.CENTER)) {
                text(pick.name, txt(9f, ink, 16f, 600))
                text(pct(pick.pickRate), txt(8f, green, 13f, 600))
            }
        }
    }

    private fun ElementBuilder.heroStat(label: String, value: Any?, valueColor: Int = white) {
        element(CssStyle(width = px(135), height = px(52), padding = Edges(6f, 8f), background = Color.makeRGB(45, 49, 70), borderRadius = 10f, alignItems = AlignItems.CENTER)) {
            text(label, txt(9f, Color.makeRGB(148, 156, 184), 15f, 600).copy(textAlign = TextAlign.CENTER))
            text(value, txt(14f, valueColor, 23f, 700).copy(textAlign = TextAlign.CENTER))
        }
    }

    private fun ElementBuilder.sectionHeader(title: String, id: String? = null) {
        element(CssStyle(direction = FlexDirection.ROW, width = percent(100), height = px( 48), padding = Edges( 11f, 16f), justifyContent = JustifyContent.SPACE_BETWEEN, alignItems = AlignItems.CENTER), id = id) {
            element(CssStyle(flexGrow = 1f, height = px(  28f), justifyContent = JustifyContent.CENTER)) {
                text(title, txt( 15f, ink,  20f, 700))
            }
            element(CssStyle(position = Position.ABSOLUTE, left = 0f, bottom = 0f, width = percent(100), height = px(1), background = line), id = id?.let { "$it-divider" })
        }
    }

    private fun emptyDocument(data: CharacterDetail) = TemplateDocument(
        1180,
        230,
        document(CssStyle(width = px(1180), height = px(230), padding = Edges(28f), background = pageBg)) {
            element(cardStyle(174f).copy(alignItems = AlignItems.CENTER, justifyContent = JustifyContent.CENTER)) {
                text("${data.name} · 暂无角色分析数据", txt(14f, muted, 30f, 600).copy(textAlign = TextAlign.CENTER))
            }
        },
    )

    private fun cardStyle(height: Float) = CssStyle(width = percent(100), height = px(height), background = card, border = Border(1f, line), borderRadius = 15f)
    private fun txt(size: Float, color: Int, height: Float, weight: Int = 400) = CssStyle(width = percent(100), height = px(height), fontSize = size, fontWeight = weight, color = color)
    private fun pct(value: Double) = "%.1f%%".format(value)
    private fun signed(value: Double) = if (value > 0.0) "+%.1f".format(value) else "%.1f".format(value)
    private fun rpColor(value: Double) = when {
        value > 0.0 -> green
        value < 0.0 -> red
        else -> muted
    }
    private fun String?.resolve(base: String) = this?.takeIf(String::isNotBlank)?.let { if (it.startsWith("http") || it.startsWith("file:")) it else base.trimEnd('/') + "/" + it.trimStart('/') }
}
