package cn.luorenmu.plugins.match

import cn.luorenmu.nutdraw.NutDraw
import cn.luorenmu.nutdraw.dom.NutElement
import cn.luorenmu.nutdraw.dom.NutNode
import cn.luorenmu.nutdraw.dom.NutText
import cn.luorenmu.nutdraw.layout.FlexLayoutEngine
import cn.luorenmu.nutdraw.layout.LayoutBox
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

class MatchDetailDesignTest {
    private val root = Path.of(System.getProperty("match.design.root"))
    @TempDir lateinit var images: Path

    private fun fixtureImage(name: String): String {
        val image = images.resolve("$name.svg")
        Files.createDirectories(image.parent)
        Files.writeString(image, """<svg xmlns="http://www.w3.org/2000/svg" width="32" height="32"><rect width="32" height="32" rx="3" fill="#dbe4ef"/></svg>""")
        return image.toUri().toString()
    }

    private fun fixture(): MatchDetailData {
        val names = listOf("露米娅观察员", "星河", "流光", "백수", "삼다수는맛있어", "月下听风", "指挥官", "碧海", "청춘", "风信子", "澄空", "Lumia", "神圣审判", "悠然", "晨雾", "라온", "渡鸦", "初雪", "南柯", "HelloWorld", "雪落", "云间", "星尘", "해피")
        val characters = listOf("杰琪", "阿雅", "玄佑", "马格努斯", "菲奥娜", "娜汀", "扎希尔", "哈特")
        val icons = listOf("103504.png", "103701.png", "104501.png", "108701.png", "110503.png").map {
            fixtureImage("item/$it")
        }
        val portraits = (1..8).map { fixtureImage("character/$it") }
        val backgrounds = listOf(5, 4, 4, 5, 6).map {
            fixtureImage("item/bg/$it")
        }
        return MatchDetailData(
            gameId = "DEMO-0001", mode = "排位赛", server = "亚一", startedAt = "2026-09-30 20:18",
            durationSeconds = 1311, version = "12.4", focusedPlayerId = 4, designSample = true,
            teams = (1..8).map { team ->
                val players = (0..2).map { index ->
                    val n = (team - 1) * 3 + index
                    MatchPlayer(id = (n + 1).toLong(), nickname = names[n], character = characters[n % 8], portrait = portraits[n % 8],
                        kills = (24 - n) % 8, deaths = n % 4, assists = (n + 3) % 13,
                        damage = if (n == 3) 143256 else (4200 + (24 - n) * 480).toLong(),
                        taken = if (n == 3) 182560 else (6500 + n * 640).toLong(),
                        survivalSeconds = (1311 - (team - 1) * 120).toLong(), rp = if (n == 3) 123456 else 6450 + n * 32,
                        rpChange = 55 - n * 5, healing = (n * 103).toLong(), vision = (18 + n).toLong(), credits = (1060 + n * 22).toLong(),
                        equipment = icons, equipmentBackgrounds = backgrounds,
                        weaponIcon = fixtureImage("weapon/${listOf(1, 10, 11, 13)[n % 4]}"),
                        traitSkillIcon = fixtureImage("trait/skill/${listOf(7000201, 7000401, 7000501, 7000601)[n % 4]}"),
                        tacticalSkillIcon = fixtureImage("tactical/skill/${listOf(500020, 500030, 500120, 500170)[n % 4]}"),
                        tierName = listOf("钻石 II", "秘银", "陨石", "永恒")[n % 4],
                        tierIcon = fixtureImage("tier/round/${listOf(6, 7, 8, 63)[n % 4]}"))
                }
                MatchTeam(team, team, players.sumOf { it.kills ?: 0 }, players)
            }
        )
    }

    private fun layout(data: MatchDetailData): LayoutBox {
        val doc = MatchDetailTemplate().build(data)
        return FlexLayoutEngine().layout(doc.root, doc.width.toFloat(), doc.height.toFloat())
    }

    @Test fun `focused nickname is red in summary and player card without changing other names`() {
        val red = org.jetbrains.skia.Color.makeRGB(220, 38, 38)
        val ink = org.jetbrains.skia.Color.makeRGB(32, 38, 49)
        val data = fixture()
        val box = layout(data)
        assertEquals(red, assertNotNull(box.find("overview-focus")).node.style.color)
        assertEquals(red, assertNotNull(box.find("overview-top-player")).node.style.color)
        data.teams.flatMap { it.players }.forEach { player ->
            assertEquals(if (player.id == data.focusedPlayerId) red else ink,
                assertNotNull(box.find("nickname-${player.id}")).node.style.color)
        }
        val allPlayers = layout(data.copy(focusedPlayerId = null))
        assertEquals(ink, assertNotNull(allPlayers.find("overview-focus")).node.style.color)
        assertEquals(ink, assertNotNull(allPlayers.find("overview-top-player")).node.style.color)
    }

    @Test fun `live match removes settlement disclaimer and its empty footer space without shrinking fonts`() {
        val sample = fixture()
        val live = sample.copy(designSample = false)
        val template = MatchDetailTemplate()
        assertNull(layout(live).find("data-note"))
        assertNotNull(layout(sample).find("data-note"), "Design examples must still be labelled as samples")
        assertEquals(template.build(sample).height - 56, template.build(live).height)
        assertEquals(34f, assertNotNull(layout(live).find("match-title")).node.style.fontSize)
    }

    @Test fun `all players have consistent aligned metric boxes and fixed fonts`() {
        val data = fixture()
        val box = layout(data)
        data.teams.forEach { team ->
            assertEquals(350f, assertNotNull(box.find("team-title-${team.number}")).bounds.width)
            assertEquals(280f, assertNotNull(box.find("team-summary-${team.number}")).bounds.width)
            val first = assertNotNull(box.find("damage-${team.players.first().id}"))
            team.players.forEach { player ->
                val card = assertNotNull(box.find("team-${team.number}"))
                val row = assertNotNull(box.find("player-${player.id}"))
                val damage = assertNotNull(box.find("damage-${player.id}"))
                val taken = assertNotNull(box.find("taken-${player.id}"))
                assertTrue(row.bounds.left >= card.bounds.left && row.bounds.right <= card.bounds.right)
                assertTrue(row.bounds.top >= card.bounds.top && row.bounds.bottom <= card.bounds.bottom)
                assertEquals(first.bounds.left, damage.bounds.left)
                assertEquals(145f, damage.bounds.width)
                assertEquals(22f, damage.node.style.fontSize)
                assertEquals(damage.bounds.top, taken.bounds.top)
                assertEquals(2, assertNotNull(box.find("damage-${player.id}-cell")).children.size)
                assertEquals(2, assertNotNull(box.find("taken-${player.id}-cell")).children.size)
                assertEquals(18f, assertNotNull(box.find("nickname-${player.id}")).node.style.fontSize)
            }
        }
    }

    @Test fun `sample and unknown data are explicit and preserve Korean names`() {
        val data = fixture()
        assertTrue(layout(data).node.texts().any { it.contains("非实时战绩") })
        assertEquals("백수", (assertNotNull(layout(data).find("nickname-4")).node as NutText).value)
        val unknown = data.copy(teams = listOf(MatchTeam(1, null, null, listOf(MatchPlayer(999, "测试", "未知角色")))))
        val unknownBox = layout(unknown)
        assertEquals("暂无", (assertNotNull(unknownBox.find("damage-999")).node as NutText).value)
        assertTrue(unknownBox.node.texts().any { it.contains("暂无排名") })
    }

    @Test fun `long nickname is truncated without shrinking its font`() {
        val data = fixture()
        val player = data.teams[0].players[0].copy(nickname = "这是一个特别长的玩家名字대한민국LongName")
        val box = layout(data.copy(teams = listOf(data.teams[0].copy(players = listOf(player)))))
        val node = assertNotNull(box.find("nickname-${player.id}")).node as NutText
        assertEquals(18f, node.style.fontSize)
        assertTrue(node.value.endsWith("…"))
    }

    @Test fun `empty state stays inside the page`() {
        val box = layout(fixture().copy(teams = emptyList()))
        val empty = assertNotNull(box.find("empty-state"))
        val note = assertNotNull(box.find("data-note"))
        assertTrue(empty.bounds.bottom <= note.bounds.top)
        assertTrue(note.bounds.bottom <= box.bounds.bottom)
    }

    @Test fun `plugin registers the real match query command`() {
        assertIs<MatchDetailCommand>(MatchPlugin().commands.single())
    }

    @Test fun `equipment quality backgrounds and player tiers are rendered without taller rows`() {
        val data = fixture()
        val box = layout(data)
        val player = data.teams.first().players.first()
        assertEquals(player.equipmentBackgrounds[0], assertNotNull(box.find("equipment-slot-${player.id}-0")).node.style.backgroundImage)
        assertEquals(5, assertNotNull(box.find("equipment-${player.id}")).children.size)
        assertEquals(player.tierName, (assertNotNull(box.find("tier-name-${player.id}")).node as NutText).value)
        assertNotNull(box.find("tier-icon-${player.id}"))
        assertEquals(88f, assertNotNull(box.find("player-${player.id}")).bounds.height)
        val unknown = layout(data.copy(teams = listOf(MatchTeam(1, 1, 0, listOf(MatchPlayer(999, "测试", "未知"))))))
        assertEquals("段位未知", (assertNotNull(unknown.find("tier-name-999")).node as NutText).value)
    }

    @Test fun `entire RP line uses sign colors without shrinking fonts`() {
        val box = layout(fixture())
        val positive = assertNotNull(box.find("rp-change-1"))
        val negative = assertNotNull(box.find("rp-change-13"))
        val zero = assertNotNull(box.find("rp-change-12"))
        assertEquals(org.jetbrains.skia.Color.makeRGB(25, 128, 81), positive.node.style.color)
        assertEquals(org.jetbrains.skia.Color.makeRGB(194, 58, 58), negative.node.style.color)
        assertEquals(org.jetbrains.skia.Color.makeRGB(99, 107, 119), zero.node.style.color)
        assertEquals(positive.node.style.color, assertNotNull(box.find("rp-1")).node.style.color)
        assertEquals(negative.node.style.color, assertNotNull(box.find("rp-13")).node.style.color)
        assertEquals(zero.node.style.color, assertNotNull(box.find("rp-12")).node.style.color)
        assertEquals(14f, positive.node.style.fontSize)
        assertEquals("+55", (positive.node as NutText).value)
        assertEquals("-5", (negative.node as NutText).value)
        val highRp = assertNotNull(box.find("rp-4"))
        val highDelta = assertNotNull(box.find("rp-change-4"))
        assertTrue(highRp.bounds.right <= highDelta.bounds.left)
        assertTrue(highRp.bounds.width + highDelta.bounds.width + 4 <= 116)
    }

    @Test fun `weapon and trait tactical icons fit existing rows`() {
        val box = layout(fixture())
        assertNotNull(box.find("weapon-1"))
        val equipment = assertNotNull(box.find("equipment-1"))
        val skills = assertNotNull(box.find("skills-1"))
        assertEquals(5, equipment.children.size)
        assertEquals(2, skills.children.size)
        assertTrue(equipment.bounds.right <= skills.bounds.left)
        assertTrue(assertNotNull(box.find("tier-1")).bounds.right <= skills.bounds.left)
        assertEquals(assertNotNull(box.find("damage-1")).bounds.right, assertNotNull(box.find("trait-group-1")).bounds.right)
        assertEquals(assertNotNull(box.find("taken-1")).bounds.right, assertNotNull(box.find("tactical-group-1")).bounds.right)
        assertEquals("天赋", (assertNotNull(box.find("trait-label-1")).node as NutText).value)
        assertEquals("战术", (assertNotNull(box.find("tactical-label-1")).node as NutText).value)
        assertNotNull(box.find("trait-skill-1"))
        assertNotNull(box.find("tactical-skill-1"))
        assertEquals(88f, assertNotNull(box.find("player-1")).bounds.height)
    }

    @Test fun `teams have distinct stable colors independent of ranking`() {
        val data = fixture()
        val box = layout(data)
        val colors = data.teams.map { team ->
            val color = assertNotNull(box.find("team-title-${team.number}")).node.style.color
            assertEquals(color, assertNotNull(box.find("team-color-${team.number}")).node.style.background)
            assertTrue(assertNotNull(box.find("team-color-${team.number}")).bounds.right <
                assertNotNull(box.find("team-title-${team.number}")).bounds.left)
            color
        }
        assertEquals(8, colors.distinct().size)
        val reordered = layout(data.copy(teams = data.teams.map { it.copy(rank = 9 - (it.rank ?: 0)) }))
        data.teams.forEachIndexed { index, team ->
            assertEquals(colors[index], assertNotNull(reordered.find("team-title-${team.number}")).node.style.color)
        }
    }

    @Test fun `render the design preview with local images only`() = runBlocking {
        val output = root.resolve("build/previews/match-detail-design.png")
        NutDraw.render(MatchDetailTemplate(), fixture(), output)
        assertTrue(Files.size(output) > 10000)
    }

    private fun LayoutBox.find(id: String): LayoutBox? = takeIf { node.id == id } ?: children.firstNotNullOfOrNull { it.find(id) }
    private fun NutNode.texts(): List<String> = when (this) {
        is NutText -> listOf(value)
        is NutElement -> children.flatMap { it.texts() }
        else -> emptyList()
    }
}
