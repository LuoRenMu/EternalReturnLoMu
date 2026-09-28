package cn.luorenmu.plugins.equipment

import cn.luorenmu.nutdraw.NutDraw
import cn.luorenmu.nutdraw.layout.FlexLayoutEngine
import cn.luorenmu.nutdraw.layout.LayoutBox
import cn.luorenmu.request.api.entity.response.dakgg.DakGGItemsResponse.Item
import cn.luorenmu.request.api.impl.EternalReturnDakGGApi
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

class ItemQueryTest {
    private fun LayoutBox.findById(id: String): LayoutBox? =
        if (node.id == id) this else children.firstNotNullOfOrNull { it.findById(id) }

    private val items = listOf(
        Item(id = 1, name = "军刀", type = "Weapon"),
        Item(id = 2, name = "军刀改", type = "Weapon"),
        Item(id = 3, name = "军刀材料", type = "Misc"),
        Item(id = 4, name = "护甲", type = "Armor"),
    )

    @Test fun `equipment search handles exact partial phonetic id and missing input`() {
        assertEquals(listOf(1L), findEquipment(items, " 军刀 ").map { it.id })
        assertEquals(listOf(1L), findEquipment(items, "JUNDAO").map { it.id })
        assertEquals(listOf(4L), findEquipment(items, "4").map { it.id })
        assertEquals(listOf(1L, 2L), findEquipment(items, "军").map { it.id })
        assertTrue(findEquipment(items, "军刀材料").isEmpty())
        assertTrue(findEquipment(items, "").isEmpty())
        assertTrue(findEquipment(items, "不存在").isEmpty())
    }

    @Test fun `all six official backgrounds and unknown fallback`() {
        listOf("Common", "Uncommon", "Rare", "Epic", "Legend", "Mythic").forEachIndexed { i, grade ->
            assertEquals(i + 1, equipmentGradeNumber(grade))
            assertTrue(equipmentBackground(grade).endsWith("0${i + 1}.svg"))
        }
        assertEquals(1, equipmentGradeNumber("Unknown"))
    }

    @Test fun `tooltip removes game markup and wraps long paragraphs`() {
        assertEquals(listOf("攻击力 +10", "荣誉 15 & 20"), equipmentDescription("<b>攻击力 +10</b><br/>荣誉 <color=#fff>15</color> &amp; 20"))
        assertEquals(listOf(38, 38, 24), equipmentDescription("测".repeat(100)).map { it.length })
    }

    @Test fun `long item card fits in calculated bounds`() {
        val item = items.first().copy(tooltip = "长说明".repeat(100), grade = "Epic")
        val doc = ItemQueryTemplate().build(ItemQueryData(item, List(5) { Item(name = "材料$it", imageUrl = "https://example.com/$it.png", grade = "Rare") }))
        val layout = FlexLayoutEngine().layout(doc.root, doc.width.toFloat(), doc.height.toFloat())
        fun check(box: cn.luorenmu.nutdraw.layout.LayoutBox) {
            box.children.forEach {
                assertTrue(it.bounds.right <= box.bounds.right + 0.5f)
                assertTrue(it.bounds.bottom <= box.bounds.bottom + 0.5f)
                check(it)
            }
        }
        check(layout)
        repeat(5) { i ->
            assertNotNull(layout.findById("material-image-$i"))
            assertNotNull(layout.findById("material-name-$i"))
            assertEquals(equipmentBackground("Rare"), layout.findById("material-background-$i")?.node?.style?.backgroundImage)
        }
        assertEquals(equipmentBackground("Epic"), layout.findById("item-grade-background")?.node?.style?.backgroundImage)
    }

    @Test fun `live equipment preview`() = runBlocking {
        if (System.getenv("REAL_EQUIPMENT_PREVIEW") != "true") return@runBlocking
        val items = EternalReturnDakGGApi.Data.GetItems.execute().items
        val item = items.first { it.type == "Weapon" && it.grade == "Legend" }
        val dir = Path.of(System.getProperty("user.dir"), "build", "previews")
        Files.createDirectories(dir)
        val materials = item.makeMaterials.orEmpty().map { id -> items.first { it.id == id } }
        NutDraw.render(ItemQueryTemplate(), ItemQueryData(item, materials), dir.resolve("equipment-real.png"))
        assertTrue(Files.size(dir.resolve("equipment-real.png")) > 100)
    }
}
