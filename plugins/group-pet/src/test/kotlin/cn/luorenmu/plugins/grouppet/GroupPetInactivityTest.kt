package cn.luorenmu.plugins.grouppet

import cn.luorenmu.plugins.qqmedia.QQGroupPermissionResult
import cn.luorenmu.plugins.qqmedia.QQGroupPermissionStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import love.forte.simbot.message.Message
import love.forte.simbot.message.PlainText
import love.forte.simbot.message.toText
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Duration
import kotlin.test.*

class GroupPetInactivityTest {
    @TempDir lateinit var root: Path
    private val clock = MutableClock()
    private val group = "A".repeat(32)
    private val otherGroup = "B".repeat(32)
    private fun repository(name: String = "pets.db") = GroupPetRepository(root.resolve(name))
    private fun player(age: Duration = Duration.ofDays(3)) =
        PetSnapshot(7, "백수", PetMatch(100, clock.millis() - age.toMillis(), 41))
    private fun granted() = QQGroupPermissionResult(QQGroupPermissionStatus.ENABLED)

    @Test fun `inactivity starts at three full days and does not render a match`() = runBlocking {
        val repo = repository()
        val snapshot = player(Duration.ofDays(3).minusHours(1))
        repo.add(group, "member", snapshot, clock.millis())
        val sent = mutableListOf<Message>()
        val service = GroupPetService(repo, { snapshot }, { error("inactivity must not render images") },
            { _, message -> sent += message }, clock, permission = { granted() })
        clock.advance(Duration.ofMinutes(30))
        assertEquals(0, service.tick())
        clock.advance(Duration.ofMinutes(30))
        assertEquals(1, service.tick())
        assertEquals("主人，您的宠物\"백수\"已经3天0时0分没有游玩了，您应该关注一下他/她的状态喵~",
            assertIs<PlainText>(sent.single()).text)
        assertEquals(1L, repo.pushCount())
    }

    @Test fun `inactivity uses the current nickname and exact elapsed days hours and minutes`() = runBlocking {
        val repo = repository()
        val snapshot = player(Duration.ofDays(3).plusHours(2).plusMinutes(3))
        repo.add(group, "member", snapshot, clock.millis())
        val sent = mutableListOf<Message>()
        val service = GroupPetService(repo, { snapshot.copy(nickname = "阿玖백수") }, { error("no image") },
            { _, message -> sent += message }, clock, permission = { granted() })
        clock.advance(Duration.ofMinutes(30))
        assertEquals(1, service.tick())
        assertEquals("主人，您的宠物\"阿玖백수\"已经3天2时33分没有游玩了，您应该关注一下他/她的状态喵~",
            assertIs<PlainText>(sent.single()).text)
    }

    @Test fun `each group is reminded once and the marker survives a repository and service restart`() = runBlocking {
        val repo = repository()
        val snapshot = player()
        repo.add(group, "member", snapshot, clock.millis())
        repo.add(otherGroup, "member", snapshot, clock.millis())
        var queries = 0
        val sent = mutableListOf<String>()
        fun service(repository: GroupPetRepository) = GroupPetService(repository, { queries++; snapshot },
            { error("no image") }, { target, _ -> sent += target }, clock, permission = { granted() })
        clock.advance(Duration.ofMinutes(30))
        assertEquals(2, service(repo).tick())
        assertEquals(1, queries, "multiple groups share the same match query")
        assertEquals(setOf(group, otherGroup), sent.toSet())
        clock.advance(Duration.ofMinutes(30))
        assertEquals(0, service(repository()).tick())
        clock.advance(Duration.ofHours(2))
        assertEquals(0, service(repository()).tick())
        assertEquals(2, sent.size)
        assertEquals(2L, repository().pushCount())
    }

    @Test fun `a new match is delivered normally and can start another inactivity period`() = runBlocking {
        val repo = repository()
        var snapshot = player()
        repo.add(group, "member", snapshot, clock.millis())
        var renders = 0
        val sent = mutableListOf<Message>()
        val service = GroupPetService(repo, { snapshot }, { renders++; "match image".toText() },
            { _, message -> sent += message }, clock, permission = { granted() })
        clock.advance(Duration.ofMinutes(30))
        assertEquals(1, service.tick())
        snapshot = snapshot.copy(latest = PetMatch(101, clock.millis(), 41))
        clock.advance(Duration.ofMinutes(30))
        assertEquals(1, service.tick())
        assertEquals(1, renders)
        assertEquals(101L, repo.list(group).single().lastMatchId)
        clock.advance(Duration.ofDays(3))
        assertEquals(1, service.tick())
        clock.advance(Duration.ofMinutes(30))
        assertEquals(0, service.tick())
        assertEquals(2, sent.filterIsInstance<PlainText>().size)
        assertEquals(3L, repo.pushCount())
    }

    @Test fun `failed inactivity sends retry independently without marking the successful group again`() = runBlocking {
        val repo = repository()
        val snapshot = player()
        repo.add(group, "member", snapshot, clock.millis())
        repo.add(otherGroup, "member", snapshot, clock.millis())
        var failing = true
        val sent = mutableListOf<String>()
        val service = GroupPetService(repo, { snapshot }, { error("no image") }, { target, _ ->
            if (target == group && failing) throw IllegalArgumentException("send rejected")
            sent += target
        }, clock, permission = { granted() })
        clock.advance(Duration.ofMinutes(30))
        assertEquals(1, service.tick())
        assertEquals(1, repo.list(group).single().failures)
        assertEquals(2, repo.list(group).size + repo.list(otherGroup).size)
        assertEquals(1L, repo.pushCount())
        failing = false
        clock.advance(Duration.ofMinutes(30))
        assertEquals(1, service.tick())
        assertEquals(listOf(otherGroup, group), sent)
        assertEquals(2L, repo.pushCount())
    }

    @Test fun `missing invalid future or regressed match evidence never triggers an inactivity notice`() = runBlocking {
        val snapshot = player()
        val match = checkNotNull(snapshot.latest)
        val cases = listOf(null, match.copy(startedAt = null), match.copy(startedAt = 0),
            match.copy(startedAt = -1), match.copy(startedAt = clock.millis() + Duration.ofDays(1).toMillis()),
            match.copy(id = 99))
        cases.forEachIndexed { index, latest ->
            val repo = repository("unknown-$index.db")
            repo.add(group, "member", snapshot, clock.millis())
            val service = GroupPetService(repo, { snapshot.copy(latest = latest) }, { error("no image") },
                { _, _ -> error("unreliable evidence must not notify") }, clock, permission = { granted() })
            clock.advance(Duration.ofMinutes(30))
            assertEquals(0, service.tick())
            assertEquals(0, repo.list(group).single().failures)
        }
    }

    @Test fun `disabled proactive permission pauses inactivity without querying and re-enabling can notify`() = runBlocking {
        val repo = repository()
        val snapshot = player()
        repo.add(group, "member", snapshot, clock.millis())
        var allowed = false
        var queries = 0
        var sends = 0
        val service = GroupPetService(repo, { queries++; snapshot }, { error("no image") }, { _, _ -> sends++ }, clock,
            permission = { QQGroupPermissionResult(if (allowed) QQGroupPermissionStatus.ENABLED else QQGroupPermissionStatus.DISABLED) })
        clock.advance(Duration.ofMinutes(30))
        assertEquals(0, service.tick())
        assertEquals(0, queries)
        allowed = true
        clock.advance(Duration.ofMinutes(5))
        assertEquals(1, service.tick())
        assertEquals(1, sends)
    }

    @Test fun `expired or cancelled subscriptions do not send inactivity notices`() = runBlocking {
        val repo = repository()
        val snapshot = player()
        repo.add(group, "member", snapshot, clock.millis())
        val endNotices = mutableListOf<Message>()
        val expired = GroupPetService(repo, { error("expired must not query") }, { error("no image") },
            { _, message -> endNotices += message }, clock, permission = { granted() })
        clock.advance(Duration.ofDays(7))
        assertEquals(1, expired.tick())
        assertFalse(assertIs<PlainText>(endNotices.single()).text.contains("没有游玩"))
        repo.add(group, "member", snapshot, clock.millis())
        val cancelled = GroupPetService(repo, { repo.remove(group, 7); snapshot }, { error("no image") },
            { _, _ -> error("cancelled must not send") }, clock, permission = { granted() })
        clock.advance(Duration.ofHours(2))
        assertEquals(0, cancelled.tick())
        assertTrue(repo.list(group).isEmpty())
    }

    @Test fun `shutdown cancellation does not consume an inactivity notice`() = runBlocking {
        val repo = repository()
        val snapshot = player()
        repo.add(group, "member", snapshot, clock.millis())
        val cancelled = GroupPetService(repo, { snapshot }, { error("no image") },
            { _, _ -> throw CancellationException("plugin shutdown") }, clock, permission = { granted() })
        clock.advance(Duration.ofMinutes(30))
        assertFailsWith<CancellationException> { cancelled.tick() }
        assertEquals(0L, repo.pushCount())
        clock.advance(Duration.ofMinutes(5))
        val resumed = GroupPetService(repository(), { snapshot }, { error("no image") },
            { _, _ -> }, clock, permission = { granted() })
        assertEquals(1, resumed.tick())
    }
}
