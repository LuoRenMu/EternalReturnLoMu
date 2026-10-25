package cn.luorenmu.plugins.equipment

import cn.luorenmu.nutdraw.NutDraw
import cn.luorenmu.nutdraw.layout.FlexLayoutEngine
import cn.luorenmu.nutdraw.layout.LayoutBox
import cn.luorenmu.request.api.entity.response.dakgg.DakGGItemsResponse.Item
import cn.luorenmu.request.api.entity.response.dakgg.DakGGCharactersResponse.DakGGCharacterById
import cn.luorenmu.request.api.entity.response.dakgg.DakGGWeaponResponse.Weapon
import cn.luorenmu.request.api.impl.EternalReturnDakGGApi
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
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

    @Test fun `recipe tree expands nested ingredients and preserves repeated materials`() {
        val root = Item(id = 10, name = "成品", makeMaterials = listOf(20, 30))
        val intermediate = Item(id = 20, name = "中间装备", makeMaterial1 = 40, makeMaterial2 = 40)
        val other = Item(id = 30, name = "另一材料")
        val leaf = Item(id = 40, name = "基础材料")
        val tree = buildItemRecipeTree(root, listOf(root, intermediate, other, leaf))
        assertEquals(listOf(20L, 30L), tree.children.map { it.item.id })
        assertEquals(listOf(40L, 40L), tree.children[0].children.map { it.item.id })
        assertEquals(3, layoutItemRecipeTree(tree).count { it.node.children.isEmpty() })
    }

    @Test fun `recipe tree stops cyclic references`() {
        val root = Item(id = 10, makeMaterials = listOf(20))
        val child = Item(id = 20, makeMaterials = listOf(10))
        val tree = buildItemRecipeTree(root, listOf(root, child))
        assertEquals(10L, tree.children.single().children.single().item.id)
        assertTrue(tree.children.single().children.single().children.isEmpty())
    }

    @Test fun `commander armor uses only direct ingredients at each tree level`() {
        val shirt = Item(id = 202106, name = "白衬衫")
        val patch = Item(id = 401307, name = "皮革补丁")
        val lighter = Item(id = 401107, name = "打火机")
        val graphite = Item(id = 401124, name = "石墨")
        val leather = Item(id = 202201, name = "皮甲", makeMaterial1 = 202106, makeMaterial2 = 401307,
            makeMaterials = listOf(202106, 401307))
        val diamond = Item(id = 401219, name = "钻石", makeMaterial1 = 401107, makeMaterial2 = 401124,
            makeMaterials = listOf(401107, 401124))
        val armor = Item(id = 202412, name = "指挥官战甲", makeMaterial1 = 202201, makeMaterial2 = 401219,
            makeMaterials = listOf(202106, 202201, 401107, 401124, 401219, 401307))
        val tree = buildItemRecipeTree(armor, listOf(armor, leather, diamond, shirt, patch, lighter, graphite))
        assertEquals(listOf("皮甲", "钻石"), tree.children.map { it.item.name })
        assertEquals(listOf("白衬衫", "皮革补丁"), tree.children[0].children.map { it.item.name })
        assertEquals(listOf("打火机", "石墨"), tree.children[1].children.map { it.item.name })
        assertEquals(7, layoutItemRecipeTree(tree).size)
    }

    @Test fun `item stats rank unique characters by character weapon pick rate`() {
        val response = Json { ignoreUnknownKeys = true }.decodeFromString<EquipmentItemStatsResponse>("""
            {"meta":{"dt":7,"tier":"diamond_plus","updatedAt":123},"itemStatSnapshot":{"itemStats":[
              {"key":202524,"characterWeaponStats":[
                {"key":[1,10],"count":45,"count2":100,"win":9},
                {"key":[2,10],"count":30,"count2":50,"win":12},
                {"key":[1,11],"count":70,"count2":100,"win":21},
                {"key":[3,10],"count":1,"count2":200,"win":0},
                {"key":[4,10],"count":90,"count2":100,"win":40}
              ]}]}}
        """.trimIndent())
        val characters = (1L..3L).map { DakGGCharacterById(id = it, name = "角色$it") }
        val weapons = listOf(Weapon(id = 10, name = "单手剑"), Weapon(id = 11, name = "双手剑"))
        val ranked = topItemCharacters(202524, response, characters, weapons)
        assertEquals(listOf(1L, 2L), ranked.map { it.characterId })
        assertEquals("双手剑", ranked[0].weaponName)
        assertEquals(70.0, ranked[0].usageRate)
        assertEquals(30.0, ranked[0].winRate)
        assertEquals("60.0%", usagePercent(ranked[1].usageRate))
        assertTrue(topItemCharacters(999, response, characters, weapons).isEmpty())
    }

    @Test fun `long item card fits in calculated bounds`() {
        val item = items.first().copy(tooltip = "长说明".repeat(100), grade = "Epic")
        val recipe = ItemRecipeNode(item, List(5) { ItemRecipeNode(Item(name = "材料$it", imageUrl = "https://example.com/$it.png", grade = "Rare")) })
        val doc = ItemQueryTemplate().build(ItemQueryData(item, recipe,
            List(5) { ItemCharacterUsage(it.toLong(), "角色$it", "双手剑", "https://example.com/$it.png", 50.0, 20.0, 100) }))
        val layout = FlexLayoutEngine().layout(doc.root, doc.width.toFloat(), doc.height.toFloat())
        fun check(box: cn.luorenmu.nutdraw.layout.LayoutBox) {
            box.children.forEach {
                assertTrue(it.bounds.right <= box.bounds.right + 0.5f)
                assertTrue(it.bounds.bottom <= box.bounds.bottom + 0.5f)
                check(it)
            }
        }
        check(layout)
        assertNotNull(layout.findById("recipe-tree"))
        repeat(6) { i ->
            assertNotNull(layout.findById("recipe-node-$i"))
            if (i > 0) assertNotNull(layout.findById("material-image-$i"))
            assertNotNull(layout.findById("material-name-$i"))
            assertEquals(equipmentBackground(if (i == 0) "Epic" else "Rare"),
                layout.findById("material-background-$i")?.node?.style?.backgroundImage)
        }
        assertEquals(equipmentBackground("Epic"), layout.findById("item-grade-background")?.node?.style?.backgroundImage)
        repeat(5) { i ->
            assertNotNull(layout.findById("character-$i"))
            assertNotNull(layout.findById("character-usage-$i"))
        }
    }

    @Test fun `live equipment preview`() = runBlocking {
        if (System.getenv("REAL_EQUIPMENT_PREVIEW") != "true") return@runBlocking
        val items = EternalReturnDakGGApi.Data.GetItems.execute().items
        val previewId = System.getenv("REAL_EQUIPMENT_PREVIEW_ID")?.toLongOrNull()
        val item = if (previewId == null) items.first { it.type == "Weapon" && it.grade == "Legend" }
            else items.first { it.id == previewId }
        val dir = Path.of(System.getProperty("user.dir"), "build", "previews")
        Files.createDirectories(dir)
        val recipe = buildItemRecipeTree(item, items)
        val stats = EquipmentStatsClient.fetch()
        val characters = EternalReturnDakGGApi.Data.GetCharacters.execute().characters
        val weapons = EternalReturnDakGGApi.Data.GetWeapons.execute().masteries
        val usage = localizeItemPortraits(topItemCharacters(item.id, stats, characters, weapons), characters)
        assertTrue(usage.isNotEmpty(), "Live item stats should contain high-usage characters")
        assertTrue(usage.all { it.imageUrl.isNotBlank() }, "Live character portraits should have URLs")
        NutDraw.render(ItemQueryTemplate(), ItemQueryData(item, recipe, usage), dir.resolve("equipment-real.png"))
        assertTrue(Files.size(dir.resolve("equipment-real.png")) > 100)
    }

    @Test fun `recipe tree preview`() = runBlocking {
        if (System.getenv("PREVIEW_EQUIPMENT_TREE") != "true") return@runBlocking
        val base = Item(id = 3, name = "短剑", grade = "Common")
        val second = Item(id = 4, name = "铁矿石", grade = "Common")
        val branch = Item(id = 2, name = "强化短剑", grade = "Rare", makeMaterials = listOf(3, 4))
        val root = Item(id = 1, name = "传奇长剑", type = "Weapon", grade = "Legend",
            tooltip = "攻击力 +70<br/>暴击率 +15%", makeMaterials = listOf(2, 3))
        val dir = Path.of(System.getProperty("user.dir"), "build", "previews")
        Files.createDirectories(dir)
        NutDraw.render(ItemQueryTemplate(), ItemQueryData(root,
            buildItemRecipeTree(root, listOf(root, branch, base, second))), dir.resolve("equipment-tree.png"))
        assertTrue(Files.size(dir.resolve("equipment-tree.png")) > 100)
    }
}
