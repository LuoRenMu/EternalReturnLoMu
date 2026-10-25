package cn.luorenmu.plugins.character

import cn.luorenmu.nutdraw.layout.FlexLayoutEngine
import cn.luorenmu.nutdraw.layout.LayoutBox
import cn.luorenmu.nutdraw.dom.NutImage
import cn.luorenmu.nutdraw.dom.NutText
import cn.luorenmu.service.entity.CharacterDetail
import org.jetbrains.skia.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 *
 * @author LoMu
 * Date 2026/8/16 15:30
 */
class CharacterDetailTemplateRegressionTest {
    @Test
    fun `render current detail layout preview with fixture data`() = kotlinx.coroutines.runBlocking {
        val output = java.nio.file.Path.of(System.getProperty("user.dir"), "build", "previews", "character-detail-current-fixture.png")
        java.nio.file.Files.createDirectories(output.parent)
        cn.luorenmu.nutdraw.NutDraw.render(CharacterDetailTemplate(), characterDetail(), output)
        assertTrue(java.nio.file.Files.size(output) > 100)
    }

    @Test
    fun `only first two skill builds are rendered`() {
        val root = CharacterDetailTemplate().build(characterDetail()).root
        assertNotNull(root.findById("skill-plan-0"))
        assertNotNull(root.findById("skill-plan-1"))
        kotlin.test.assertNull(root.findById("skill-plan-2"))
        kotlin.test.assertNull(root.findById("skill-plan-3"))
        val layout = FlexLayoutEngine().layout(root, 1000f, 10000f)
        val panel = assertNotNull(layout.findById("skill-panel"))
        assertEquals(0, panel.descendants().count { it.node is NutImage })
        repeat(2) { index ->
            val plan = assertNotNull(layout.findById("skill-plan-$index"))
            assertEquals(110f, plan.bounds.bottom - plan.bounds.top)
            val priority = assertNotNull(layout.findById("skill-priority-$index"))
            assertEquals(listOf("T", "Q", "E", "W"), (0..3).map { keyIndex ->
                (assertNotNull(layout.findById("skill-key-$index-$keyIndex")).node as NutText).value
            })
            assertEquals(3, priority.descendants().count { it.node is NutText && (it.node as NutText).value == "→" })
            assertEquals(22f, assertNotNull(layout.findById("skill-key-$index-0")).node.style.fontSize)
        }
    }

    @Test
    fun `priority icons match slots in priority order not metadata order and missing icons retain keys`() {
        val data = characterDetail()
        val analysis = assertNotNull(data.analysis)
        val skills = listOf("Q", "W", "E", "R", "T").mapIndexed { index, slot ->
            CharacterDetail.SkillSlot(index.toLong(),slot,"技能 $slot","file:///skills/$slot.png")
        }
        val weapon = analysis.weapons.first().copy(skills = skills, skillBySlot = skills.associateBy { it.slot })
        val doc = CharacterDetailTemplate().build(data.copy(analysis = analysis.copy(weapons = listOf(weapon))))
        val layout = FlexLayoutEngine().layout(doc.root,doc.width.toFloat(),doc.height.toFloat())
        for (plan in 0..1) for ((index, slot) in listOf("T", "Q", "E", "W").withIndex()) {
            val image = assertNotNull(layout.findById("skill-icon-$plan-$index")).node as NutImage
            assertEquals("file:///skills/$slot.png",image.source)
        }
        assertEquals(8,assertNotNull(layout.findById("skill-panel")).descendants().count { it.node is NutImage })
        val missing = weapon.copy(skills = emptyList(), skillBySlot = emptyMap())
        val empty = CharacterDetailTemplate().build(data.copy(analysis = analysis.copy(weapons = listOf(missing))))
        val missingLayout = FlexLayoutEngine().layout(empty.root,empty.width.toFloat(),empty.height.toFloat())
        kotlin.test.assertNull(missingLayout.findById("skill-icon-0-0"))
        assertEquals("T",(assertNotNull(missingLayout.findById("skill-key-0-0")).node as NutText).value)
    }

    @Test
    fun `selected weapon and its tier become the displayed build`() {
        val original = characterDetail()
        val selected = original.selectWeapon("2")
        assertEquals("其他武器", selected.analysis?.weapons?.first()?.weapon)
        assertEquals("A", selected.analysis?.characterTier)
        assertEquals(selected, original.selectWeapon("其他武器"))
        assertEquals(1, original.analysis?.weapons?.first()?.weaponId?.toInt())
        kotlin.test.assertFailsWith<cn.luorenmu.exception.MessageReplyException> { original.selectWeapon("999") }
    }

    @Test
    fun `weapon buttons fill input without execution and preserve rank`() {
        val options = CharacterDetailOptions(rank = cn.luorenmu.request.entity.module.DakGGRank.MITHRIL_PLUS)
        val reply = characterWeaponChoices(characterDetail(), options, true) as love.forte.simbot.message.Messages
        val keyboard = reply.filterIsInstance<love.forte.simbot.component.qguild.message.QGKeyboards>().single()
        val buttons = keyboard.keyboards.content.rows.flatMap { it.buttons }
        assertEquals(2, buttons.size)
        buttons.forEachIndexed { index, button ->
            assertEquals(2, button.action?.type)
            assertEquals(false, button.action?.enter)
            assertEquals(false, button.action?.reply)
            assertEquals("/查询角色 1 排位 无暇 ${index + 1}", button.action?.data)
            assertEquals("character-weapon-1-${index + 1}", button.id)
        }
        val plain = characterWeaponChoices(characterDetail(), options, false)
        assertTrue(plain is love.forte.simbot.message.Text)
    }

    @Test
    fun `detail shows tier image and pick names`() {
        val document = CharacterDetailTemplate().build(characterDetail())
        val layout = FlexLayoutEngine().layout(document.root, document.width.toFloat(), document.height.toFloat())
        val tierIcon = assertNotNull(layout.findById("detail-tier-icon"))
        val characterName = assertNotNull(layout.findById("detail-character-name"))

        assertEquals("#1 / 10", (assertNotNull(layout.findById("detail-character-ranking")).node as NutText).value)
        assertEquals("测试 · 战士", (assertNotNull(layout.findById("detail-character-role")).node as NutText).value)
        assertTrue(tierIcon.node.style.backgroundImage?.endsWith("/character-tier-S.svg") == true)
        assertTrue(tierIcon.node !is NutImage)
        assertTrue(tierIcon.bounds.right <= characterName.bounds.left)
        assertTrue(assertNotNull(layout.findById("main-weapon-tier-icon")).node.style.backgroundImage?.endsWith("/character-tier-S.svg") == true)
        assertTrue((assertNotNull(layout.findById("main-weapon-rp")).node as NutText).value.contains("+4.5"))
        assertTrue(assertNotNull(layout.findById("other-weapon-tier-icon-0")).node.style.backgroundImage?.endsWith("/character-tier-A.svg") == true)
        assertTrue((assertNotNull(layout.findById("other-weapon-rp-0")).node as NutText).value.contains("-2.5"))
        repeat(4) { augmentIndex ->
            repeat(6) { subIndex ->
                val name = assertNotNull(layout.findById("augment-sub-name-$augmentIndex-$subIndex"))
                assertTrue((name.node as NutText).value.isNotBlank())
            }
        }
        repeat(5) { slotIndex ->
            repeat(5) { itemIndex ->
                val name = assertNotNull(layout.findById("equipment-name-$slotIndex-$itemIndex"))
                assertTrue((name.node as NutText).value.isNotBlank())
                assertTrue((assertNotNull(layout.findById("equipment-pick-rate-$slotIndex-$itemIndex")).node as NutText).value.startsWith("选"))
                assertTrue((assertNotNull(layout.findById("equipment-win-rate-$slotIndex-$itemIndex")).node as NutText).value.startsWith("胜"))
            }
        }
    }

    @Test
    fun `tier images stay compact and other weapons do not render avatar placeholders`() {
        val document = CharacterDetailTemplate().build(characterDetail())
        val layout = FlexLayoutEngine().layout(document.root, document.width.toFloat(), document.height.toFloat())

        val detailTier = assertNotNull(layout.findById("detail-tier-icon"))
        val mainWeaponTier = assertNotNull(layout.findById("main-weapon-tier-icon"))
        val otherWeapon = assertNotNull(layout.findById("other-weapon-0"))
        val otherWeaponTier = assertNotNull(layout.findById("other-weapon-tier-icon-0"))
        val ranking = assertNotNull(layout.findById("main-weapon-ranking"))

        assertTrue(detailTier.bounds.right - detailTier.bounds.left <= 28f)
        assertTrue(mainWeaponTier.bounds.right - mainWeaponTier.bounds.left <= 20f)
        assertTrue(otherWeaponTier.bounds.right - otherWeaponTier.bounds.left <= 22f)
        assertEquals(0, otherWeapon.descendants().count { it.node is NutImage })
        assertEquals(Color.TRANSPARENT, otherWeapon.node.style.background)
        assertEquals("主武器排名 #1 / 10", (ranking.node as NutText).value)
        assertTrue(ranking.node.style.fontSize >= 12f)
    }

    @Test
    fun `detail palette uses unfilled surfaces pink accents and readable dark text`() {
        val root = CharacterDetailTemplate().build(characterDetail()).root
        val hero = assertNotNull(root.findById("detail-hero"))

        assertEquals(Color.WHITE, root.style.background)
        assertEquals(Color.makeRGB(48, 38, 45), root.style.color)
        assertEquals(Color.TRANSPARENT, hero.style.background)
        assertEquals(Color.TRANSPARENT, assertNotNull(root.findById("main-weapon-strip")).style.background)
        assertEquals(Color.makeRGB(170, 48, 94), assertNotNull(root.findById("skill-key-0-0")).style.color)
        val doc = CharacterDetailTemplate().build(characterDetail())
        val layout = FlexLayoutEngine().layout(doc.root,doc.width.toFloat(),doc.height.toFloat())
        layout.descendants().filter { it.node is cn.luorenmu.nutdraw.dom.NutElement && it.bounds.width * it.bounds.height > 2000f }.forEach {
            assertTrue(it.node.style.background in listOf(Color.TRANSPARENT,Color.WHITE),"Colored surface: ${it.node.id}")
        }
    }

    @Test
    fun `card headers do not draw square borders inside rounded panels`() {
        val document = CharacterDetailTemplate().build(characterDetail())
        val layout = FlexLayoutEngine().layout(document.root, document.width.toFloat(), document.height.toFloat())

        listOf("tactical-header", "augment-header", "loadout-header", "equipment-header").forEach { id ->
            val header = assertNotNull(layout.findById(id))
            assertTrue(header.node.style.border.width == 0f, "$id border width=${header.node.style.border.width}")
            assertNotNull(layout.findById("$id-divider"))
        }
    }

    @Test
    fun `augment panel stays inside document`() {
        val document = CharacterDetailTemplate().build(characterDetail())
        val layout = FlexLayoutEngine().layout(document.root, document.width.toFloat(), document.height.toFloat())
        val panel = assertNotNull(layout.findById("augment-panel"))

        assertTrue(panel.bounds.right <= document.width, "panel right=${panel.bounds.right}, document width=${document.width}")
        assertTrue(panel.bounds.bottom <= document.height, "panel bottom=${panel.bounds.bottom}, document height=${document.height}")
        panel.descendants().forEach { child ->
            assertTrue(child.bounds.right <= panel.bounds.right, "child right=${child.bounds.right}, panel right=${panel.bounds.right}")
            assertTrue(child.bounds.bottom <= panel.bounds.bottom, "child bottom=${child.bounds.bottom}, panel bottom=${panel.bounds.bottom}")
        }
    }

    @Test
    fun `equipment statistics show five items per slot without overflowing`() {
        val document = CharacterDetailTemplate().build(characterDetail())
        val layout = FlexLayoutEngine().layout(document.root, document.width.toFloat(), document.height.toFloat())
        val panel = assertNotNull(layout.findById("equipment-panel"))

        repeat(5) { slotIndex ->
            val row = assertNotNull(layout.findById("equipment-slot-$slotIndex"))
            repeat(5) { itemIndex ->
                val item = assertNotNull(layout.findById("equipment-item-$slotIndex-$itemIndex"))
                assertTrue(item.bounds.right <= row.bounds.right)
                assertTrue(item.bounds.bottom <= row.bounds.bottom)
                item.descendants().forEach { child ->
                    assertTrue(child.bounds.right <= item.bounds.right, "child right=${child.bounds.right}, item right=${item.bounds.right}")
                    assertTrue(child.bounds.bottom <= item.bounds.bottom, "child bottom=${child.bounds.bottom}, item bottom=${item.bounds.bottom}")
                }
            }
        }
        assertTrue(panel.bounds.right <= document.width, "panel right=${panel.bounds.right}, document width=${document.width}")
        assertTrue(panel.bounds.bottom <= document.height, "panel bottom=${panel.bounds.bottom}, document height=${document.height}")
    }

    @Test
    fun `all text is readable and sections stay in bounds`() {
        val document = CharacterDetailTemplate().build(characterDetail())
        val layout = FlexLayoutEngine().layout(document.root, document.width.toFloat(), document.height.toFloat())
        layout.descendants().filter { it.node is NutText }.forEach {
            assertTrue(it.node.style.fontSize >= 16f, "Small text: ${(it.node as NutText).value}")
            assertTrue(it.bounds.right <= document.width && it.bounds.bottom <= document.height)
        }
        (listOf(layout) + layout.descendants()).forEach { parent ->
            parent.children.forEach { child ->
                assertTrue(child.bounds.right <= parent.bounds.right + 0.5f,
                    "Overflow: ${child.node.id} right=${child.bounds.right}, parent=${parent.bounds.right}")
                assertTrue(child.bounds.bottom <= parent.bounds.bottom + 0.5f,
                    "Overflow: ${child.node.id} bottom=${child.bounds.bottom}, parent=${parent.bounds.bottom}")
            }
        }
        assertEquals(1000, document.width)
    }

    @Test
    fun `equipment only retains loadouts and slot picks but hides unrelated sections`() {
        val doc = CharacterDetailTemplate(equipmentOnly = true).build(characterDetail())
        val layout = FlexLayoutEngine().layout(doc.root, doc.width.toFloat(), doc.height.toFloat())
        assertNotNull(layout.findById("loadout-panel"))
        assertNotNull(layout.findById("equipment-panel"))
        kotlin.test.assertNull(layout.findById("other-weapons-panel"))
        kotlin.test.assertNull(layout.findById("infusion-panel"))
        kotlin.test.assertNull(layout.findById("players-panel"))
        assertTrue(doc.height < CharacterDetailTemplate().build(characterDetail()).height)
    }

    @Test
    fun `learning and tactical panels align side by side with readable type`() {
        val doc = CharacterDetailTemplate().build(characterDetail())
        val layout = FlexLayoutEngine().layout(doc.root, doc.width.toFloat(), doc.height.toFloat())
        val skill = assertNotNull(layout.findById("skill-panel"))
        val tactical = assertNotNull(layout.findById("tactical-panel"))
        assertEquals(skill.bounds.top, tactical.bounds.top)
        assertEquals(skill.bounds.bottom, tactical.bounds.bottom)
        assertEquals(16f, tactical.bounds.left - skill.bounds.right)
        assertEquals(536f, skill.bounds.width)
        assertEquals(384f, tactical.bounds.width)
        assertEquals(22f, assertNotNull(layout.findById("skill-priority-0")).node.style.fontSize)
        repeat(4) { assertNotNull(layout.findById("tactical-choice-$it")) }
        assertEquals(Color.makeRGB(21, 115, 83), assertNotNull(layout.findById("main-weapon-rp")).node.style.color)
        assertEquals(Color.makeRGB(190, 48, 57), assertNotNull(layout.findById("other-weapon-rp-0")).node.style.color)
    }

    @Test
    fun `sparse and missing sections do not overflow and keep empty state usable`() {
        val sample = characterDetail()
        val analysis = assertNotNull(sample.analysis)
        val weapon = analysis.weapons.first()
        val variants = listOf(weapon.copy(skillBuilds = emptyList()), weapon.copy(tacticals = emptyList()),
            weapon.copy(skillBuilds = weapon.skillBuilds.take(1), tacticals = weapon.tacticals.take(1)),
            weapon.copy(skillBuilds = emptyList(), tacticals = emptyList(), augments = emptyList(),
                itemBuilds = emptyList(), topEquipments = emptyList(), infusions = emptyList()))
        for (variant in variants) {
            val doc = CharacterDetailTemplate().build(sample.copy(analysis = analysis.copy(weapons = listOf(variant))))
            val layout = FlexLayoutEngine().layout(doc.root, doc.width.toFloat(), doc.height.toFloat())
            (listOf(layout) + layout.descendants()).forEach { parent -> parent.children.forEach { child ->
                assertTrue(child.bounds.right <= parent.bounds.right + 0.5f,"${child.node.id} right overflow")
                assertTrue(child.bounds.bottom <= parent.bounds.bottom + 0.5f,"${child.node.id} bottom overflow")
            } }
        }
        val empty = CharacterDetailTemplate().build(sample.copy(analysis = null))
        assertEquals(220,empty.height)
        val emptyLayout = FlexLayoutEngine().layout(empty.root, 1000f, 220f)
        assertTrue(emptyLayout.descendants().any { it.node is NutText && (it.node as NutText).value.contains("暂无数据") })
    }

    private fun characterDetail(): CharacterDetail {
        val slots = listOf("武器", "胸甲", "头部", "手臂", "腿部")
        val equipments = slots.flatMapIndexed { slotIndex, slot ->
            (0 until 5).map { itemIndex ->
                CharacterDetail.EquipmentSlotPick(
                    slot = slot,
                    id = (slotIndex * 10 + itemIndex).toLong(),
                    name = "装备$itemIndex",
                    iconUrl = "",
                    bgUrl = "",
                    pickRate = if (itemIndex == 0) 100.0 else 20.0 - itemIndex,
                    winRate = 40.0 + itemIndex,
                )
            }
        }
        val weapon = CharacterDetail.WeaponBuild(
            weaponId = 1,
            weapon = "测试武器",
            iconUrl = "",
            tier = "S",
            tierScore = 100.0,
            rpChange = 4.5,
            games = 100,
            pickRate = 50.0,
            winRate = 20.0,
            top3Rate = 40.0,
            avgRank = 3.0,
            avgKills = 2.0,
            rank = 1,
            rankSize = 10,
            skills = emptyList(),
            skillBySlot = emptyMap(),
            skillBuilds = List(4) { CharacterDetail.SkillBuild(listOf("T", "Q", "E", "W"), List(15) { "Q" }, 32.5, 18.7) },
            topEquipments = equipments,
            itemBuilds = List(4) { CharacterDetail.ItemBuild(List(5) { pick(it.toLong()) }, emptyList(), 12.3, 18.9) },
            tacticals = List(4) { pick(it.toLong()) },
            augments = (0 until 4).map { index ->
                CharacterDetail.Augment(
                    core = pick(index.toLong()),
                    subs = (0 until 6).map { subIndex -> pick((index * 10 + subIndex + 10).toLong()) },
                )
            },
            infusions = List(10) { pick(it.toLong()) },
        )
        return CharacterDetail(
            id = 1,
            name = "测试角色",
            title = "测试",
            imageUrl = "",
            archetypes = listOf("Warrior", "None"),
            analysis = CharacterDetail.CharacterAnalysis(
                tier = "diamond_plus",
                tierLabel = "钻石+",
                matchingModeLabel = "排位",
                teamModeLabel = "三排",
                updatedLabel = "刚刚",
                patchLabel = "1.0",
                totalGames = 100,
                characterGames = 50,
                pickRate = 50.0,
                characterTier = "S",
                weapons = listOf(
                    weapon,
                    weapon.copy(
                        weaponId = 2,
                        weapon = "其他武器",
                        tier = "A",
                        tierScore = 80.0,
                        rpChange = -2.5,
                        rank = 2,
                    ),
                ),
                topPlayers = emptyList(),
            ),
            httpServer = "",
        )
    }

    private fun pick(id: Long) = CharacterDetail.Pick(
        id = id,
        name = "潜能$id",
        iconUrl = "",
        bgUrl = "",
        pickRate = 10.0,
        winRate = 10.0,
    )

    private fun LayoutBox.findById(id: String): LayoutBox? =
        takeIf { node.id == id } ?: children.firstNotNullOfOrNull { it.findById(id) }

    private fun LayoutBox.descendants(): List<LayoutBox> = children + children.flatMap { it.descendants() }
}
