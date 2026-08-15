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

class CharacterDetailTemplateRegressionTest {
    @Test
    fun `detail shows tier image and pick names`() {
        val document = CharacterDetailTemplate().build(characterDetail())
        val layout = FlexLayoutEngine().layout(document.root, document.width.toFloat(), document.height.toFloat())
        val tierIcon = assertNotNull(layout.findById("detail-tier-icon"))
        val characterName = assertNotNull(layout.findById("detail-character-name"))

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

        assertTrue(detailTier.bounds.right - detailTier.bounds.left <= 26f)
        assertTrue(mainWeaponTier.bounds.right - mainWeaponTier.bounds.left <= 18f)
        assertTrue(otherWeaponTier.bounds.right - otherWeaponTier.bounds.left <= 20f)
        assertEquals(0, otherWeapon.descendants().count { it.node is NutImage })
        assertEquals(Color.makeRGB(235, 237, 243), otherWeapon.node.style.background)
        assertEquals("实验体排名 #1 / 10", (ranking.node as NutText).value)
        assertTrue(ranking.node.style.fontSize >= 12f)
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
            skillBuilds = emptyList(),
            topEquipments = equipments,
            itemBuilds = emptyList(),
            tacticals = emptyList(),
            augments = (0 until 4).map { index ->
                CharacterDetail.Augment(
                    core = pick(index.toLong()),
                    subs = (0 until 6).map { subIndex -> pick((index * 10 + subIndex + 10).toLong()) },
                )
            },
            infusions = emptyList(),
        )
        return CharacterDetail(
            id = 1,
            name = "测试角色",
            title = "测试",
            imageUrl = "",
            archetypes = emptyList(),
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
