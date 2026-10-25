package cn.luorenmu.plugins.match

import cn.luorenmu.command.entity.MessageSender
import cn.luorenmu.exception.MessageReplyException
import cn.luorenmu.request.api.entity.response.dakgg.DakGGCharactersResponse
import cn.luorenmu.request.api.entity.response.dakgg.DakGGTiersResponse
import cn.luorenmu.request.api.entity.response.game.BattleUserGamesResponse.UserGame
import cn.luorenmu.nutdraw.NutDraw
import cn.luorenmu.service.ResourcesDownloadService
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import love.forte.simbot.common.id.ID
import love.forte.simbot.common.id.StringID
import love.forte.simbot.message.OfflineImage
import love.forte.simbot.message.PlainText
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

class MatchDetailQueryTest {
    private val root = Path.of(System.getProperty("match.design.root"))
    private val characters = DakGGCharactersResponse(characters = arrayListOf(
        DakGGCharactersResponse.DakGGCharacterById(id = 1, name = "杰琪")))
    private val tiers = DakGGTiersResponse(arrayListOf(DakGGTiersResponse.EternalReturnTier(id = 6, name = "钻石")))

    private fun game(id: Long, team: Long = 1) = UserGame(
        userNum = id, nickname = if (id == 2L) "백수" else "玩家$id", gameId = 123, seasonId = 41,
        teamNumber = team, gameRankVirtual = 1, teamKill = 7, characterNum = 1, skinCode = 1001,
        matchingMode = 3, damageToPlayer = 123456, mmrAfter = 8123, mmrGain = -21,
        startDtm = "2026-10-01T01:02:03.000+0000", duration = 1200, playTime = 1195, survivableTime = 40, bestWeapon = 1,
        traitFirstCore = 7000101, tacticalSkillGroup = 500010,
        equipmentVirtual = JsonArray(listOf(101, 102, 103, 104, 105, 106).map(::JsonPrimitive)),
        equipmentGradeVirtual = JsonArray(listOf(4, 5, 4, 5, 6, 4).map(::JsonPrimitive)),
        masteryLevel = emptyMap(), traitSecondSub = emptyList(), scoredPoint = emptyList(), creditSource = null,
        itemTransferredConsole = emptyList(), itemTransferredDrone = emptyList(), collectItemForLog = emptyList(),
        isLeavingBeforeCreditRevivalTerminate = false,
    )

    @Test fun `mapper focuses anonymized detail ID and preserves totals tiers and five equipment slots`() {
        val snapshot = MatchSnapshot(listOf(game(1), game(2), game(3)), mapOf(2L to MatchPlayerTier(6, 2)),
            mapOf(2L to MatchPlayerExtras(98765, 37, 1800)))
        val data = MatchDetailMapper.assemble(snapshot, "백수", characters, tiers)
        assertFalse(data.designSample)
        assertEquals(2, data.focusedPlayerId)
        assertEquals(7, data.teams.single().kills) // not 21: upstream teamKill is already a team total
        val player = data.teams.single().players[1]
        assertEquals(98765, player.taken)
        assertEquals(37, player.vision)
        assertEquals(1800, player.credits)
        assertEquals(-21, player.rpChange)
        assertEquals(1195, player.survivalSeconds)
        assertEquals("钻石 2", player.tierName)
        assertEquals("2026-10-01 09:02:03", data.startedAt)
        assertEquals(5, player.equipment.size)
        assertEquals(5, player.equipmentBackgrounds.size)
        assertTrue(player.equipment[0].orEmpty().endsWith("/images/item/101.png"))
        assertTrue(player.weaponIcon.orEmpty().startsWith("file:/"))
        assertNull(data.teams.single().players[0].taken)
    }

    @Test fun `cobalt and missing values are not disguised as ranked RP or fabricated metrics`() {
        val data = MatchDetailMapper.assemble(MatchSnapshot(listOf(game(1).copy(matchingMode = 6,
            equipmentVirtual = JsonObject(mapOf("0" to JsonPrimitive(101), "4" to JsonPrimitive(105))),
            startDtm = ""))), null, characters, tiers)
        val player = data.teams.single().players.single()
        assertEquals("钴协议", data.mode)
        assertEquals("时间未知", data.startedAt)
        assertNull(player.rp)
        assertNull(player.rpChange)
        assertNull(player.taken)
        assertNull(player.tierName)
        assertNull(player.equipment[1])
        assertTrue(player.equipment[4].orEmpty().endsWith("105.png"))
    }

    @Test fun `empty duplicate or wrong game data is rejected`() {
        assertFailsWith<MessageReplyException> { MatchDetailMapper.validate(emptyList(), 123) }
        assertFailsWith<MessageReplyException> { MatchDetailMapper.validate(listOf(game(1)), 124) }
        assertFailsWith<MessageReplyException> { MatchDetailMapper.validate(listOf(game(1), game(1)), 123) }
        assertFailsWith<MessageReplyException> { MatchDetailMapper.decode(buildJsonObject {}, 123) }
    }

    @Test fun `command validates args before querying and accepts season zero`() = runBlocking {
        var queries = 0
        val data = MatchDetailMapper.assemble(MatchSnapshot(listOf(game(1))), "玩家1", characters, tiers)
        val command = MatchDetailCommand(query = { id, name, season ->
            queries++
            assertEquals(123, id)
            assertEquals("玩家1", name)
            assertEquals(0, season)
            data
        }, render = { actual, path ->
            assertEquals(data, actual)
            Files.write(path, byteArrayOf(1))
        })
        val args = mapOf("gameId" to "123", "nickname" to "玩家1", "seasonId" to "0")
        for (invalid in listOf(emptyMap(), args + ("gameId" to "-1"), args + ("seasonId" to "-1"),
            args - "nickname", args - "seasonId", args + ("nickname" to "bad/name"))) {
            assertIs<PlainText>(command.listen(sender(), invalid))
        }
        assertEquals(0, queries)
        assertIs<OfflineImage>(command.listen(sender(), args))
        assertEquals(1, queries)
    }

    @Test fun `design samples and mismatched data never enter query output`() = runBlocking {
        val data = MatchDetailMapper.assemble(MatchSnapshot(listOf(game(1))), null, characters, tiers)
        for (invalid in listOf(data.copy(gameId = "999"), data.copy(designSample = true))) {
            val command = MatchDetailCommand(query = { _, _, _ -> invalid }, render = { _, _ -> fail("must not render") })
            assertFailsWith<IllegalStateException> { command.listen(sender(), mapOf("gameId" to "123")) }
        }
    }

    @Test fun `captured real DAK settlement decodes all players and renders without sample data`() = runBlocking {
        val fixture = root.resolve("build/match-detail-live.json")
        if (!Files.isRegularFile(fixture)) return@runBlocking // optional local public-data integration fixture
        val snapshot = MatchDetailMapper.decode(Json.parseToJsonElement(Files.readString(fixture)).jsonObject, 65932204)
        assertEquals(21, snapshot.games.size)
        val data = MatchDetailMapper.assemble(snapshot, "백수", characters, tiers)
        assertEquals(21, data.teams.sumOf { it.players.size })
        assertNotNull(data.focusedPlayerId)
        val focused = data.teams.flatMap { it.players }.first { it.id == data.focusedPlayerId }
        assertEquals("백수", focused.nickname)
        assertNotNull(focused.taken)
        assertFalse(data.designSample)
        val target = root.resolve("build/previews/match-detail-live.png")
        Files.createDirectories(target.parent)
        NutDraw.render(MatchDetailTemplate(), data, target)
        assertTrue(Files.size(target) > 100)
    }

    @Test fun `live match endpoint resources and settlement image work end to end`() = runBlocking {
        if (System.getenv("REAL_MATCH_PREVIEW") != "true") return@runBlocking
        val data = MatchDetailSource.query(65932204, "백수", 41, ResourcesDownloadService())
        assertEquals(21, data.teams.sumOf { it.players.size })
        assertFalse(data.designSample)
        val focused = data.teams.flatMap { it.players }.first { it.id == data.focusedPlayerId }
        assertEquals("백수", focused.nickname)
        assertFalse(focused.character.startsWith("角色 "))
        assertNotNull(focused.tierName)
        assertEquals(1344, focused.survivalSeconds)
        assertTrue(Files.isRegularFile(Path.of(java.net.URI(assertNotNull(focused.portrait)))))
        assertTrue(focused.equipment.all { it != null && Files.isRegularFile(Path.of(java.net.URI(it))) })
        val target = root.resolve("build/previews/match-detail-live-complete.png")
        Files.createDirectories(target.parent)
        NutDraw.render(MatchDetailTemplate(), data, target)
        assertTrue(Files.size(target) > 100)
        println("Real match: ${data.gameId}, ${data.teams.size} teams, 21 players; preview: $target")
    }

    @Test fun `live cobalt season zero shows real teams without ranked RP`() = runBlocking {
        if (System.getenv("REAL_MATCH_PREVIEW") != "true") return@runBlocking
        val data = MatchDetailSource.query(65959632, "백수", 0, ResourcesDownloadService())
        assertEquals(8, data.teams.sumOf { it.players.size })
        assertEquals("钴协议", data.mode)
        val focused = data.teams.flatMap { it.players }.first { it.id == data.focusedPlayerId }
        assertEquals("백수", focused.nickname)
        assertNull(focused.rp)
        val target = root.resolve("build/previews/match-detail-cobalt-live.png")
        Files.createDirectories(target.parent)
        NutDraw.render(MatchDetailTemplate(), data, target)
        assertTrue(Files.size(target) > 100)
        println("Real cobalt match: ${data.gameId}, 8 players; preview: $target")
    }

    private fun sender(): MessageSender {
        val id = StringID::class.java.getMethod("valueOf", String::class.java).invoke(null, "match-test") as ID
        return MessageSender(id, "tester", id, "/查询对局", "/查询对局")
    }
}
