package cn.luorenmu.plugins.grouppet

import cn.luorenmu.command.entity.MessageSender
import cn.luorenmu.request.api.entity.response.game.BattleUserGamesResponse.UserGame
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonNull
import love.forte.simbot.common.id.StringID.Companion.ID
import love.forte.simbot.message.toText
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant
import kotlin.test.*

class GroupPetCommandTest {
    @TempDir lateinit var root: Path

    @Test fun `name only sync and anonymized match ids support compact timezone offset`() {
        DakGroupPetSource.validateSync(cn.luorenmu.request.api.entity.response.dakgg.DakGGSyncResponse(playerName = "莉莉维丝"))
        val key = DakGroupPetSource.subscriptionKey("莉莉维丝")
        assertTrue(key > 1)
        assertNotEquals(key, DakGroupPetSource.subscriptionKey("其他玩家"))
        assertEquals(key, DakGroupPetSource.subscriptionKey("莉莉维丝"))
        val snapshot = DakGroupPetSource.snapshot(key, "莉莉维丝", listOf(game().copy(
            userNum = 1, gameId = 66829052, startDtm = "2026-10-22T17:30:14.014+0900", seasonId = 41,
        )), verifyIdentity = false)
        assertEquals(Instant.parse("2026-10-22T08:30:14.014Z").toEpochMilli(), snapshot.latest!!.startedAt)
    }

    @Test fun `live source accepts current upstream payload`() = runBlocking {
        if (System.getenv("GROUP_PET_LIVE_TEST") != "true") return@runBlocking
        val result = DakGroupPetSource().latest("莉莉维丝")
        assertEquals("莉莉维丝", result.nickname)
        assertTrue(result.playerId > 1)
        assertNotNull(result.latest)
    }

    @Test fun `bare command returns markdown and keyboard without querying service`() = runBlocking {
        val command = GroupPetCommand { error("menu must not access service") }
        val sender = MessageSender("a".repeat(32).ID, "member", "b".repeat(32).ID, "/群宠", "/群宠")
        for (args in listOf(emptyMap(), mapOf("player" to "帮助"))) {
            val reply = command.listen(sender, args) as love.forte.simbot.message.Messages
            assertEquals(1, reply.filterIsInstance<love.forte.simbot.component.qguild.message.QGMarkdown>().size)
            val keyboard = reply.filterIsInstance<love.forte.simbot.component.qguild.message.QGKeyboards>().single()
            val buttons = keyboard.keyboards.content.rows.flatMap { it.buttons }
            assertEquals(listOf("/群宠 ", "/群宠 列表", "/群宠 取消 "), buttons.map { it.action?.data })
            assertTrue(buttons.all { it.action?.type == 2 && it.action?.enter == false })
        }
    }

    @Test fun `subscribe list and cancel are group scoped and duplicate command is idempotent`() = runBlocking {
        val repository = GroupPetRepository(root.resolve("subscriptions.db"))
        repository.updatePermission("a".repeat(32), true, 1)
        var queries = 0
        val service = GroupPetService(repository, {
            queries++; PetSnapshot(7, "백수", PetMatch(100, Instant.parse("2026-10-22T05:55:00Z").toEpochMilli(), 41))
        }, { "match".toText() }, { _, _ -> error("commands must not push historical games") }, MutableClock(), permission = { grantedPermission() })
        val command = GroupPetCommand { service }
        val sender = MessageSender("a".repeat(32).ID, "member", "b".repeat(32).ID, "", "")
        command.listen(sender, mapOf("player" to "백수"))
        command.listen(sender, mapOf("player" to "백수"))
        assertEquals(1, queries)
        assertEquals(1, repository.list(sender.groupOpenId.toString()).size)
        assertEquals("백수", (command.listen(sender, mapOf("player" to "列表")) as love.forte.simbot.message.PlainText).text)
        command.listen(sender.copy(groupOpenId = "c".repeat(32).ID), mapOf("player" to "取消", "nickname" to "백수"))
        assertEquals(1, repository.list(sender.groupOpenId.toString()).size)
        command.listen(sender, mapOf("player" to "取消", "nickname" to "7"))
        assertTrue(repository.list(sender.groupOpenId.toString()).isEmpty())
    }

    @Test fun `source picks newest match across seasons and maps cobalt to season zero`() {
        val games = listOf(
            game().copy(userNum = 7, gameId = 101, startDtm = "2026-10-22T14:00:00+08:00", seasonId = 41, matchingMode = 6),
            game().copy(userNum = 7, gameId = 100, startDtm = "2026-10-21T06:00:00Z", seasonId = 40),
        )
        val latest = assertNotNull(DakGroupPetSource.snapshot(7, "백수", games).latest)
        assertEquals(101, latest.id)
        assertEquals(0, latest.seasonId)
        assertEquals(Instant.parse("2026-10-22T06:00:00Z").toEpochMilli(), latest.startedAt)
        assertNull(DakGroupPetSource.snapshot(7, "백수", emptyList()).latest)
        assertFailsWith<IllegalStateException> { DakGroupPetSource.snapshot(8, "someone", games) }
    }

    private fun game() = UserGame(equipmentVirtual = JsonNull, equipmentGradeVirtual = JsonNull,
        masteryLevel = emptyMap(), traitSecondSub = emptyList(), scoredPoint = emptyList(), creditSource = null,
        itemTransferredConsole = emptyList(), itemTransferredDrone = emptyList(), collectItemForLog = emptyList(),
        isLeavingBeforeCreditRevivalTerminate = false)
}
