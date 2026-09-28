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
        assertEquals(Color.makeRGB(240, 243, 248), otherWeapon.node.style.background)
        assertEquals("主武器排名 #1 / 10", (ranking.node as NutText).value)
        assertTrue(ranking.node.style.fontSize >= 12f)
    }

    @Test
    fun `detail palette uses white surfaces and readable dark text`() {
        val root = CharacterDetailTemplate().build(characterDetail()).root
        val hero = assertNotNull(root.findById("detail-hero"))

        assertEquals(Color.WHITE, root.style.background)
        assertEquals(Color.makeRGB(24, 29, 36), root.style.color)
        assertEquals(Color.TRANSPARENT, hero.style.background)
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
