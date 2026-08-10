package cn.luorenmu.nutdraw.templates

import cn.luorenmu.nutdraw.css.*
import cn.luorenmu.nutdraw.dom.ElementBuilder
import cn.luorenmu.nutdraw.dom.document
import cn.luorenmu.service.entity.CharacterDetail
import org.jetbrains.skia.Color

/** Faithful reconstruction of character_detail.ftl + character_detail.css. */
class CharacterDetailTemplate : ImageTemplate<CharacterDetail> {
    private val bg = Color.makeRGB(245,246,250); private val white = Color.WHITE; private val ink = Color.makeRGB(26,29,40)
    private val muted = Color.makeRGB(120,128,160); private val line = Color.makeRGB(226,229,238); private val soft = Color.makeRGB(247,248,251)
    private val accent = Color.makeRGB(202,147,114); private val green = Color.makeRGB(26,170,85)

    override fun build(data: CharacterDetail): TemplateDocument {
        val analysis = data.analysis
        if (analysis == null || analysis.weapons.isEmpty()) return emptyDocument(data)
        val weapon = analysis.weapons.first()
        val skillHeight = 52 + 14 + weapon.skillBuilds.size * 112
        val itemHeight = 52 + 14 + weapon.itemBuilds.size * 132
        val equipmentHeight = if (weapon.topEquipments.isNotEmpty()) 170 else 0
        val twoColHeight = if (weapon.tacticals.isNotEmpty() || weapon.augments.isNotEmpty()) 245 else 0
        val infusionHeight = if (weapon.infusions.isNotEmpty()) 185 else 0
        val playersHeight = if (analysis.topPlayers.isNotEmpty()) 155 else 0
        val height = 24 + 154 + 18 + 145 + equipmentHeight + skillHeight + itemHeight + twoColHeight + infusionHeight + playersHeight + 80
        val base = data.httpServer

        return TemplateDocument(1180, height, document(CssStyle(width = px(1180), height = px(height), padding = Edges(24f,28f), gap = 18f, background = bg, color = ink)) {
            // cd-header
            element(sectionStyle(154f).copy(direction = FlexDirection.ROW, padding = Edges(20f), gap = 20f, alignItems = AlignItems.START)) {
                image(data.imageUrl.resolve(base), CssStyle(width = px(112), height = px(112), borderRadius = 12f, border = Border(1f,line), objectFit = ObjectFit.COVER))
                element(CssStyle(flexGrow = 1f, height = px(112), gap = 5f)) {
                    element(CssStyle(direction = FlexDirection.ROW, width = percent(100), height = px(34), alignItems = AlignItems.CENTER, gap = 10f)) {
                        text(data.name, CssStyle(width = px(220), height = px(34), fontSize = 26f, color = ink))
                        text(analysis.characterTier, tierBadge(analysis.characterTier))
                    }
                    text(data.title, txt(13f, muted, 20f))
                    element(CssStyle(direction = FlexDirection.ROW, width = percent(100), height = px(25), gap = 6f)) {
                        data.archetypes.filter { it.isNotBlank() && it != "None" }.forEach { text(it, CssStyle(width = px(it.length * 14 + 22), height = px(23), padding = Edges(2f,10f), fontSize = 12f, color = accent, background = Color.makeARGB(38,202,147,114), borderRadius = 12f, textAlign = TextAlign.CENTER)) }
                    }
                    text("登场率 ${pct(analysis.pickRate)}  ·  ${analysis.matchingModeLabel} · ${analysis.teamModeLabel}  ·  ${analysis.tierLabel}  ·  版本 ${analysis.patchLabel}  ·  更新 ${analysis.updatedLabel}", txt(12f,muted,22f))
                }
            }

            element(sectionStyle(145f)) {
                sectionTitle("武器流派")
                element(CssStyle(direction = FlexDirection.ROW, width = percent(100), height = px(92), padding = Edges(16f,18f), gap = 24f, alignItems = AlignItems.CENTER)) {
                    element(CssStyle(direction = FlexDirection.ROW, width = px(320), height = px(60), gap = 14f, alignItems = AlignItems.CENTER)) {
                        image(weapon.iconUrl.resolve(base), CssStyle(width = px(52), height = px(52), padding = Edges(5f), background = ink, borderRadius = 10f, objectFit = ObjectFit.CONTAIN))
                        element(CssStyle(width = px(245), height = px(55), gap = 3f)) { text("${weapon.weapon}   ${weapon.tier}", txt(18f,ink,27f)); text("评分 ${"%.1f".format(weapon.tierScore)} · 第 ${weapon.rank}/${weapon.rankSize} 热门", txt(12f,muted,20f)) }
                    }
                    element(CssStyle(direction = FlexDirection.ROW, flexGrow = 1f, height = px(58), gap = 8f)) {
                        stat("登场率",pct(weapon.pickRate)); stat("胜率",pct(weapon.winRate)); stat("前三率",pct(weapon.top3Rate)); stat("平均名次","#${"%.1f".format(weapon.avgRank)}"); stat("平均击杀","%.1f".format(weapon.avgKills)); stat("对局数",weapon.games)
                    }
                }
            }

            if (weapon.topEquipments.isNotEmpty()) element(sectionStyle(equipmentHeight.toFloat())) {
                sectionTitle("各部位最高选择率装备")
                element(CssStyle(direction = FlexDirection.ROW, width = percent(100), height = px(118), padding = Edges(14f,18f), gap = 12f)) {
                    weapon.topEquipments.take(5).forEach { eq -> element(CssStyle(width = px(208), height = px(105), padding = Edges(8f), background = soft, border = Border(1f,Color.makeRGB(232,235,242)), borderRadius = 10f, alignItems = AlignItems.CENTER, gap = 3f)) { text(eq.slot, txt(11f,muted,17f).copy(textAlign = TextAlign.CENTER)); element(CssStyle(width=px(48),height=px(48),padding=Edges(4f),background=white,backgroundImage=eq.bgUrl.resolve(base),border=Border(1f,line),borderRadius=10f)){ image(eq.iconUrl.resolve(base), CssStyle(width = percent(100), height = percent(100), objectFit = ObjectFit.CONTAIN)) }; text("${eq.name}  ${pct(eq.pickRate)}", txt(11f,green,18f).copy(textAlign = TextAlign.CENTER)) } }
                }
            }

            if (weapon.skillBuilds.isNotEmpty()) element(sectionStyle(skillHeight.toFloat())) {
                sectionTitle("技能学习顺序")
                element(CssStyle(width = percent(100), flexGrow = 1f, padding = Edges(14f,18f), gap = 12f)) {
                    weapon.skillBuilds.forEachIndexed { index, build -> buildRow(index, "登场率 ${pct(build.pickRate)} · 胜率 ${pct(build.winRate)}", 98f) { element(CssStyle(direction = FlexDirection.ROW, width = percent(100), height = px(48), gap = 6f, alignItems = AlignItems.CENTER)) { build.order.take(15).forEachIndexed { step, slot -> val skill = weapon.skillBySlot[slot]; element(CssStyle(width = px(42), height = px(42), gap = 0f, alignItems = AlignItems.CENTER)) { image(skill?.iconUrl.resolve(base), CssStyle(width = px(32), height = px(32), borderRadius = 7f, objectFit = ObjectFit.CONTAIN)); text("${step+1}$slot", txt(9f,muted,12f).copy(textAlign = TextAlign.CENTER)) } } } } }
                }
            }

            if (weapon.itemBuilds.isNotEmpty()) element(sectionStyle(itemHeight.toFloat())) {
                sectionTitle("推荐出装顺序")
                element(CssStyle(width = percent(100), flexGrow = 1f, padding = Edges(14f,18f), gap = 12f)) {
                    weapon.itemBuilds.forEachIndexed { index, build -> buildRow(index, "登场率 ${pct(build.pickRate)} · 胜率 ${pct(build.winRate)}", 118f) { text("最终装备", txt(11f,muted,17f)); element(CssStyle(direction = FlexDirection.ROW, width = percent(100), height = px(44), gap = 6f, alignItems = AlignItems.CENTER)) { build.items.take(5).forEachIndexed { i,item -> element(CssStyle(width=px(40),height=px(40),padding=Edges(4f),background=white,backgroundImage=item.bgUrl.resolve(base),border=Border(1f,line),borderRadius=8f)){ image(item.iconUrl.resolve(base),CssStyle(width=percent(100),height=percent(100),objectFit=ObjectFit.CONTAIN)) }; if(i < build.items.take(5).lastIndex) text("→", txt(14f,muted,20f).copy(width = px(16),textAlign = TextAlign.CENTER)) } } } }
                }
            }

            if (twoColHeight > 0) element(CssStyle(direction = FlexDirection.ROW, width = percent(100), height = px(twoColHeight), gap = 18f, alignItems = AlignItems.START)) {
                if (weapon.tacticals.isNotEmpty()) element(sectionStyle(twoColHeight.toFloat()).copy(width = px(553))) { sectionTitle("战术技能"); picks(weapon.tacticals,base,145f) }
                if (weapon.augments.isNotEmpty()) element(sectionStyle(twoColHeight.toFloat()).copy(width = px(553))) { sectionTitle("潜能"); element(CssStyle(width = percent(100), flexGrow = 1f, padding = Edges(14f,18f), gap = 10f)) { weapon.augments.take(3).forEach { aug -> element(CssStyle(direction = FlexDirection.ROW, width = percent(100), height = px(52), padding = Edges(5f), background = soft, borderRadius = 10f, gap = 8f, alignItems = AlignItems.CENTER)) { image(aug.core.iconUrl.resolve(base), CssStyle(width = px(42),height=px(42),borderRadius=8f)); aug.subs.take(6).forEach { image(it.iconUrl.resolve(base),CssStyle(width=px(34),height=px(34),borderRadius=7f)) } } } } }
            }

            if (weapon.infusions.isNotEmpty()) element(sectionStyle(infusionHeight.toFloat())) { sectionTitle("灌注选择率"); picks(weapon.infusions,base,125f) }
            if (analysis.topPlayers.isNotEmpty()) element(sectionStyle(playersHeight.toFloat())) {
                sectionTitle("高分玩家")
                element(CssStyle(direction = FlexDirection.ROW, wrap = FlexWrap.WRAP, width = percent(100), height = px(100), padding = Edges(14f,18f), gap = 10f)) {
                    analysis.topPlayers.take(10).forEach { p -> element(CssStyle(direction = FlexDirection.ROW, width = px(205), height = px(40), padding = Edges(6f,10f), background = Color.makeRGB(240,241,246), borderRadius = 8f, gap = 8f, alignItems = AlignItems.CENTER)) { image(p.tierIconUrl.resolve(base),CssStyle(width=px(28),height=px(28),objectFit=ObjectFit.CONTAIN)); text("${p.name}  ${p.mmr}",txt(11f,ink,24f).copy(flexGrow=1f)) } }
                }
            }
        })
    }

    private fun emptyDocument(data: CharacterDetail): TemplateDocument = TemplateDocument(1180,230, document(CssStyle(width=px(1180),height=px(230),padding=Edges(28f),background=bg)){ element(sectionStyle(174f).copy(alignItems=AlignItems.CENTER,justifyContent=JustifyContent.CENTER)){ text("${data.name} · 暂无角色分析数据",txt(14f,muted,30f).copy(textAlign=TextAlign.CENTER)) } })
    private fun ElementBuilder.sectionTitle(value:String) = text(value,CssStyle(width=percent(100),height=px(45),padding=Edges(12f,18f),fontSize=15f,color=ink,border=Border(1f,Color.makeRGB(232,235,242))))
    private fun ElementBuilder.stat(label:String,value:Any?) { element(CssStyle(flexGrow=1f,height=px(58),padding=Edges(6f),background=Color.makeRGB(240,241,246),borderRadius=8f,alignItems=AlignItems.CENTER)){ text(label,txt(10f,muted,16f).copy(textAlign=TextAlign.CENTER));text(value,txt(14f,ink,22f).copy(textAlign=TextAlign.CENTER)) } }
    private fun ElementBuilder.buildRow(index:Int,rates:String,height:Float,content:ElementBuilder.()->Unit){ element(CssStyle(width=percent(100),height=px(height),padding=Edges(10f,14f),background=soft,borderRadius=10f,gap=6f)){ element(CssStyle(direction=FlexDirection.ROW,width=percent(100),height=px(24),justifyContent=JustifyContent.SPACE_BETWEEN)){ text("方案 #${index+1}",CssStyle(width=px(75),height=px(22),padding=Edges(2f,12f),fontSize=11f,color=accent,background=Color.makeARGB(35,202,147,114),borderRadius=12f,textAlign=TextAlign.CENTER));text(rates,txt(12f,muted,20f).copy(width=px(250),textAlign=TextAlign.END)) };content() } }
    private fun ElementBuilder.picks(items:List<CharacterDetail.Pick>,base:String,height:Float){ element(CssStyle(direction=FlexDirection.ROW,wrap=FlexWrap.WRAP,width=percent(100),height=px(height),padding=Edges(14f,18f),gap=10f)){ items.take(12).forEach { p -> element(CssStyle(width=px(76),height=px(78),alignItems=AlignItems.CENTER,gap=3f)){ image(p.iconUrl.resolve(base),CssStyle(width=px(46),height=px(46),background=Color.makeRGB(240,241,246),border=Border(1f,line),borderRadius=8f,objectFit=ObjectFit.CONTAIN));text(p.name,txt(10f,Color.makeRGB(74,78,92),15f).copy(textAlign=TextAlign.CENTER));text("${"%.1f".format(p.pickRate)}%",txt(9f,green,13f).copy(textAlign=TextAlign.CENTER)) } } } }
    private fun sectionStyle(height:Float)=CssStyle(width=percent(100),height=px(height),background=white,border=Border(1f,line),borderRadius=14f,gap=0f)
    private fun txt(size:Float,color:Int,height:Float)=CssStyle(width=percent(100),height=px(height),fontSize=size,color=color)
    private fun tierBadge(tier:String)=CssStyle(width=px(36),height=px(30),padding=Edges(5f),fontSize=14f,color=white,textAlign=TextAlign.CENTER,background=when(tier.uppercase()){ "S"->Color.makeRGB(214,48,49);"A"->Color.makeRGB(240,100,12);"B"->Color.makeRGB(26,170,85);"C"->Color.makeRGB(46,111,214);else->Color.makeRGB(93,107,130)},borderRadius=8f)
    private fun pct(value:Double)="%.1f%%".format(value)
    private fun String?.resolve(base:String)=this?.takeIf(String::isNotBlank)?.let{if(it.startsWith("http"))it else base.trimEnd('/')+"/"+it.trimStart('/')}
}
