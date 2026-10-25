package cn.luorenmu.plugins.grouppet

import cn.luorenmu.plugins.qqmedia.QQGroupPermissionResult
import cn.luorenmu.plugins.qqmedia.QQGroupPermissionStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import love.forte.simbot.message.Message
import love.forte.simbot.message.PlainText
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Duration
import kotlin.test.*

class GroupPetExpiryTest {
    @TempDir lateinit var root: Path
    private val clock = MutableClock()
    private val group = "A".repeat(32)
    private val other = "B".repeat(32)
    private fun repository() = GroupPetRepository(root.resolve("expiry.db"))
    private fun player(id: Long = 7) = PetSnapshot(id, if (id == 7L) "백수" else "player$id",
        PetMatch(100, clock.millis() - 600_000, 41))
    private fun granted() = QQGroupPermissionResult(QQGroupPermissionStatus.ENABLED)
    private fun service(repo: GroupPetRepository, send: suspend (String, Message) -> Unit,
        permission: suspend (String) -> QQGroupPermissionResult = { granted() }) = GroupPetService(
        repo, { error("an expired subscription must not query players") }, { error("expiry must not render") }, send, clock, permission)

    private fun postponeActiveCheck(repo: GroupPetRepository) {
        val row = repo.list(group).single()
        assertTrue(repo.claim(row, "fixture", row.nextCheckAt))
        repo.finish(row.copy(nextCheckAt = row.expiresAt + Duration.ofHours(6).toMillis()), "fixture")
    }

    @Test fun `expiry sends the exact notice at seven days independently of the player's next check`() = runBlocking {
        val repo = repository()
        repo.add(group, "member", player(), clock.millis())
        postponeActiveCheck(repo)
        val sent = mutableListOf<Message>()
        val worker = service(repo, { _, message -> sent += message })
        clock.advance(Duration.ofDays(7).minusMillis(1))
        assertEquals(0, worker.tick())
        clock.advance(Duration.ofMillis(1))
        assertEquals(1, worker.tick())
        assertEquals("主人，\"LoMu\"已经暂停对您的宠物\"백수\"监视，如果还需要可以再跟我说哦。为了\"LoMu\"的身心健康，这是必要的喵！",
            assertIs<PlainText>(sent.single()).text)
        assertTrue(repo.list(group).isEmpty())
        assertEquals(1L, repo.pushCount())
        clock.advance(Duration.ofHours(6))
        assertEquals(0, service(repository(), { _, _ -> error("must not repeat after restart") }).tick())
    }

    @Test fun `expiry send failures persist a message only retry without reviving player polling`() = runBlocking {
        val repo = repository()
        repo.add(group, "member", player(), clock.millis())
        clock.advance(Duration.ofDays(7))
        var attempts = 0
        val failing = service(repo, { _, _ -> attempts++; throw IllegalArgumentException("send rejected") })
        assertEquals(0, failing.tick())
        assertEquals(1, attempts)
        assertTrue(failing.list(group).isEmpty())
        assertEquals(1, repo.list(group).single().failures)
        assertEquals(0L, repo.pushCount())
        val resumed = service(repository(), { _, _ -> attempts++ })
        assertEquals(0, resumed.tick())
        clock.advance(Duration.ofMinutes(5).minusMillis(1))
        assertEquals(0, resumed.tick())
        clock.advance(Duration.ofMillis(1))
        assertEquals(1, resumed.tick())
        assertEquals(2, attempts)
        assertEquals(1L, repo.pushCount())
        assertTrue(repo.list(group).isEmpty())
    }

    @Test fun `expiry waits for confirmed proactive permission without querying a player`() = runBlocking {
        val repo = repository()
        repo.add(group, "member", player(), clock.millis())
        clock.advance(Duration.ofDays(7))
        var permission = QQGroupPermissionStatus.DISABLED
        var sends = 0
        var checks = 0
        val worker = service(repo, { _, _ -> sends++ }, { checks++; QQGroupPermissionResult(permission) })
        assertEquals(0, worker.tick())
        assertEquals(1, checks)
        permission = QQGroupPermissionStatus.ENABLED
        assertEquals(0, worker.tick())
        assertEquals(1, checks)
        clock.advance(Duration.ofMinutes(5))
        assertEquals(1, worker.tick())
        assertEquals(1, sends)
    }

    @Test fun `expiry delivery and failures are isolated across groups`() = runBlocking {
        val repo = repository()
        repo.add(group, "member", player(), clock.millis())
        repo.add(other, "member", player(), clock.millis())
        clock.advance(Duration.ofDays(7))
        val sent = mutableListOf<String>()
        var fail = true
        val worker = service(repo, { target, _ ->
            if (target == group && fail) throw java.io.IOException("network error")
            sent += target
        })
        assertEquals(1, worker.tick())
        assertEquals(listOf(other), sent)
        assertEquals(1, repo.list(group).size)
        assertTrue(repo.list(other).isEmpty())
        fail = false
        clock.advance(Duration.ofMinutes(5))
        assertEquals(1, worker.tick())
        assertEquals(listOf(other, group), sent)
    }

    @Test fun `renewing the same player before expiry send suppresses a stale end notice`() = runBlocking {
        val repo = repository()
        val snapshot = player()
        repo.add(group, "member", snapshot, clock.millis())
        clock.advance(Duration.ofDays(7))
        val worker = service(repo, { _, _ -> error("a renewed subscription must not receive an old expiry notice") }, {
            repo.add(group, "member", snapshot.copy(latest = PetMatch(101, clock.millis(), 41)), clock.millis())
            granted()
        })
        assertEquals(0, worker.tick())
        assertEquals(101L, repo.list(group).single().lastMatchId)
        assertEquals(clock.millis() + Duration.ofDays(7).toMillis(), repo.list(group).single().expiresAt)
    }

    @Test fun `new subscriptions preserve other expired notices and only active rows count towards the cap`() = runBlocking {
        val repo = repository()
        repo.add(group, "member", player(), clock.millis())
        clock.advance(Duration.ofDays(7))
        (8L..10L).forEach { repo.add(group, "member", player(it), clock.millis()) }
        assertEquals(4, repo.list(group).size, "pending expiry must survive subscribing a different player")
        assertFailsWith<IllegalArgumentException> { repo.add(group, "member", player(11), clock.millis()) }
        val sent = mutableListOf<Message>()
        val worker = service(repo, { _, message -> sent += message })
        assertEquals(3, worker.list(group).size)
        assertEquals(1, worker.tick())
        assertContains(assertIs<PlainText>(sent.single()).text, "\"백수\"")
        assertEquals(3, repo.list(group).size)
    }

    @Test fun `manual cancellation does not produce an expiry notification`() = runBlocking {
        val repo = repository()
        repo.add(group, "member", player(), clock.millis())
        assertTrue(repo.remove(group, 7))
        clock.advance(Duration.ofDays(7))
        assertEquals(0, service(repo, { _, _ -> error("manually cancelled") }).tick())
    }

    @Test fun `shutdown cancellation leaves expiry pending for the next lease owner`() = runBlocking {
        val repo = repository()
        repo.add(group, "member", player(), clock.millis())
        clock.advance(Duration.ofDays(7))
        assertFailsWith<CancellationException> {
            service(repo, { _, _ -> throw CancellationException("shutdown") }).tick()
        }
        clock.advance(Duration.ofMinutes(5))
        assertEquals(1, service(repository(), { _, _ -> }).tick())
        assertTrue(repo.list(group).isEmpty())
    }
}
