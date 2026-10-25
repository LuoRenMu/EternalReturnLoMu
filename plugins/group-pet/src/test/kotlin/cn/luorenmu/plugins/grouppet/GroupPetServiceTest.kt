package cn.luorenmu.plugins.grouppet

import kotlinx.coroutines.runBlocking
import love.forte.simbot.message.toText
import love.forte.simbot.message.Image
import love.forte.simbot.message.Message
import love.forte.simbot.message.Messages
import love.forte.simbot.message.OfflineImage.Companion.toOfflineImage
import love.forte.simbot.message.PlainText
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.*

class GroupPetServiceTest {
    @TempDir lateinit var root: Path
    private val clock = MutableClock()
    private val group = "A".repeat(32)
    private val otherGroup = "B".repeat(32)
    private fun repo() = GroupPetRepository(root.resolve("state.db")).also {
        it.updatePermission(group, true, 1)
        it.updatePermission(otherGroup, true, 1)
    }
    private fun snapshot(id: Long? = 100, ago: Duration = Duration.ofMinutes(10)) =
        PetSnapshot(7, "백수", id?.let { PetMatch(it, clock.millis() - ago.toMillis(), 41) })

    @Test fun `new match caption uses current player name and shares one image message per group`() = runBlocking {
        val repository = repo()
        repository.add(group, "member", snapshot(), clock.millis())
        repository.add(otherGroup, "member", snapshot(), clock.millis())
        val image = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a0XcAAAAASUVORK5CYII="
        ).toOfflineImage()
        val sent = mutableListOf<Pair<String, Message>>()
        var queries = 0
        var renders = 0
        val nickname = "阿玖백수"
        val service = GroupPetService(repository, { queries++; snapshot(101).copy(nickname = nickname) },
            { renders++; image }, { target, message -> sent += target to message }, clock,
            permission = { grantedPermission() })
        clock.advance(Duration.ofMinutes(2))
        assertEquals(2, service.tick())
        assertEquals(1, queries)
        assertEquals(1, renders)
        assertEquals(setOf(group, otherGroup), sent.map { it.first }.toSet())
        assertEquals(2, sent.size, "the caption must not cause a separate send")
        for ((_, message) in sent) {
            val parts = assertIs<Messages>(message)
            assertEquals(2, parts.size)
            assertSame(image, parts.filterIsInstance<Image>().single())
            assertEquals("主人，您的宠物\"阿玖백수\"刚刚进行了一场对局喵~",
                parts.filterIsInstance<PlainText>().single().text)
        }
        assertEquals(2L, repository.pushCount(), "one image with caption counts as one push per group")
        clock.advance(Duration.ofMinutes(2))
        assertEquals(0, service.tick())
        assertEquals(2, sent.size, "unchanged matches must not repeat the image or caption")
    }

    @Test fun `illegal argument send failures retain all subscriptions including wrapped causes`() = runBlocking {
        val repository = repo()
        repository.add(group, "member", snapshot(), clock.millis())
        repository.add(group, "member", snapshot().copy(playerId = 8, nickname = "other"), clock.millis())
        clock.advance(Duration.ofMinutes(2))
        val service = GroupPetService(repository, { name -> snapshot(101).copy(
            playerId = if (name == "other") 8 else 7, nickname = name)
        }, { "image".toText() }, { _, _ ->
            throw IllegalStateException("wrapper", IllegalArgumentException("invalid parameter"))
        }, clock, permission = { grantedPermission() })
        assertEquals(0, service.tick())
        assertEquals(2, repository.list(group).size)
        assertEquals(0L, repository.pushCount(), "failed sends must not count")
        assertTrue(repository.list(group).all { it.lastMatchId == 100L && it.failures == 1 })
    }

    @Test fun `all send errors retain subscriptions and cursors retry and preserve other groups`() = runBlocking<Unit> {
        val errors = listOf(IllegalStateException("QQ rejected active send"), java.io.IOException("connection reset"),
            IllegalStateException("wrapped timeout", java.net.SocketTimeoutException()),
            IllegalArgumentException("invalid parameter"), PetPlayerNotFoundException())
        errors.forEachIndexed { index, error ->
            val repository = GroupPetRepository(root.resolve("send-failure-$index.db"))
            repository.add(group, "member", snapshot(), clock.millis())
            repository.add(group, "member", snapshot().copy(playerId = 8, nickname = "other"), clock.millis())
            repository.add(otherGroup, "member", snapshot(), clock.millis())
            clock.advance(Duration.ofMinutes(2))
            var attempts = 0
            var failing = true
            val service = GroupPetService(repository, { name -> snapshot(101).copy(
                playerId = if (name == "other") 8 else 7, nickname = name)
            }, { "image".toText() }, { target, _ ->
                attempts++
                if (target == group && failing) throw error
            }, clock, permission = { grantedPermission() })
            assertEquals(1, service.tick())
            assertEquals(3, attempts)
            assertEquals(1L, repository.pushCount(), "only the successful group counts")
            assertEquals(0L, repository.pushCount(group = group))
            assertEquals(1L, repository.pushCount(group = otherGroup))
            assertEquals(2, repository.list(group).size)
            assertTrue(repository.list(group).all { it.lastMatchId == 100L && it.failures == 1 })
            assertTrue(repository.list(group).all { it.nextCheckAt >= clock.millis() + Duration.ofMinutes(5).toMillis() })
            assertEquals(101, repository.list(otherGroup).single().lastMatchId)
            assertEquals(0, service.tick())
            assertEquals(3, attempts)
            clock.advance(Duration.ofMinutes(5))
            assertEquals(0, service.tick())
            assertEquals(5, attempts)
            assertTrue(repository.list(group).all { it.lastMatchId == 100L && it.failures == 2 })
            failing = false
            clock.advance(Duration.ofMinutes(10))
            assertEquals(2, service.tick())
            assertTrue(repository.list(group).all { it.lastMatchId == 101L && it.failures == 0 })
            assertEquals(3L, repository.pushCount())
        }
    }

    @Test fun `subscriptions expire at seven days persist and can be subscribed again`() = runBlocking {
        val repository = repo()
        var queries = 0
        val notices = mutableListOf<Message>()
        val service = GroupPetService(repository, { queries++; snapshot() }, { error("no render") },
            { _, message -> notices += message }, clock, permission = { grantedPermission() })
        val start = clock.millis()
        assertEquals("已将 백수 设为群宠。\n有效期 7 天，到期自动停止轮询，可重新订阅。\n出现新对局后主动发送最新一局\n该功能需要群主打开主动发送消息，否则不生效。\n取消：/群宠 取消 백수", service.subscribe(group, "member", "백수"))
        val expiry = start + Duration.ofDays(7).toMillis()
        assertEquals(expiry, repository.list(group).single().expiresAt)
        clock.advance(Duration.ofDays(6))
        service.subscribe(group, "member", "백수")
        assertEquals(1, queries)
        assertEquals(expiry, repo().list(group).single().expiresAt)
        assertEquals(1, repository.due(expiry - 1).size)
        assertTrue(repository.due(expiry).isEmpty())
        assertFalse(repository.claim(repository.list(group).single(), "expired", expiry))
        clock.advance(Duration.ofDays(1))
        assertEquals(1, service.tick())
        assertContains(assertIs<PlainText>(notices.single()).text, "\"LoMu\"已经暂停")
        assertEquals(1, queries)
        assertTrue(service.list(group).isEmpty())
        service.subscribe(group, "member", "백수")
        assertEquals(2, queries)
        assertEquals(expiry + Duration.ofDays(7).toMillis(), repository.list(group).single().expiresAt)
    }

    @Test fun `legacy schema migration expires undated tasks and preserves records`() {
        val path = root.resolve("legacy.db")
        Class.forName("org.sqlite.JDBC")
        java.sql.DriverManager.getConnection("jdbc:sqlite:$path").use { c ->
            c.createStatement().use {
                it.execute("""CREATE TABLE group_pet (group_id TEXT, player_id INTEGER, nickname TEXT,
                    requester_id TEXT,last_match_id INTEGER,last_match_time INTEGER,next_check_at INTEGER,
                    failures INTEGER DEFAULT 0,last_error TEXT,lease_owner TEXT,lease_until INTEGER DEFAULT 0,
                    PRIMARY KEY(group_id,player_id))""")
                it.execute("INSERT INTO group_pet(group_id,player_id,nickname,requester_id,next_check_at) VALUES ('legacy',7,'백수','member',0)")
            }
        }
        val repository = GroupPetRepository(path)
        assertEquals(0, repository.list("legacy").single().expiresAt)
        assertTrue(repository.due(clock.millis()).isEmpty())
        assertEquals(1, GroupPetRepository(path).list("legacy").size)
    }

    @Test fun `invalid and nonexistent names never create tasks`() = runBlocking {
        val repository = repo()
        var queries = 0
        val service = GroupPetService(repository, { queries++; throw PetPlayerNotFoundException() },
            { error("no render") }, { _, _ -> error("no send") }, clock, permission = { grantedPermission() })
        assertTrue(service.subscribe(group, "member", "bad name!").contains("不合法"))
        assertEquals(0, queries)
        assertFailsWith<PetPlayerNotFoundException> { service.subscribe(group, "member", "백수") }
        assertEquals(1, queries)
        assertTrue(repository.list(group).isEmpty())
    }

    @Test fun `missing player discards polling tasks but transient failure retains them`() = runBlocking {
        val repository = repo()
        repository.add(group, "member", snapshot(), clock.millis())
        repository.add(otherGroup, "member", snapshot(), clock.millis())
        clock.advance(Duration.ofMinutes(2))
        var missing = false
        var queries = 0
        val service = GroupPetService(repository, {
            queries++
            if (missing) throw PetPlayerNotFoundException()
            error("temporary failure")
        }, { error("no render") }, { _, _ -> error("no send") }, clock, permission = { grantedPermission() })
        assertEquals(0, service.tick())
        assertEquals(1, repository.list(group).size)
        missing = true
        clock.advance(Duration.ofHours(1))
        assertEquals(0, service.tick())
        assertTrue(repository.list(group).isEmpty())
        assertTrue(repository.list(otherGroup).isEmpty())
        val count = queries
        clock.advance(Duration.ofDays(1))
        service.tick()
        assertEquals(count, queries)
    }

    @Test fun `sync distinguishes explicit missing from retry and malformed data`() {
        fun response(missing: Boolean? = null, retry: Int? = null) =
            cn.luorenmu.request.api.entity.response.dakgg.DakGGSyncResponse(notFound = missing, retryAfter = retry)
        assertFailsWith<PetPlayerNotFoundException> { DakGroupPetSource.validateSync(response(true)) }
        assertFailsWith<IllegalStateException> { DakGroupPetSource.validateSync(response(true, 1000)) }
        assertFailsWith<IllegalStateException> { DakGroupPetSource.validateSync(response()) }
        DakGroupPetSource.validateSync(cn.luorenmu.request.api.entity.response.dakgg.DakGGSyncResponse(7, "백수"))
    }

    @Test fun `enabled live API permits subscription even when historical local permission is unknown or disabled`() = runBlocking {
        for ((index, permission) in listOf(null, false).withIndex()) {
            val repository = GroupPetRepository(root.resolve("permission-$index.db"))
            if (permission != null) repository.updatePermission(group, permission, 1)
            var queries = 0
            var sends = 0
            var latest = snapshot()
            val service = GroupPetService(repository, { queries++; latest }, { "image".toText() },
                { _, _ -> sends++ }, clock, permission = { grantedPermission() })
            assertEquals("已将 백수 设为群宠。\n有效期 7 天，到期自动停止轮询，可重新订阅。\n出现新对局后主动发送最新一局\n该功能需要群主打开主动发送消息，否则不生效。\n取消：/群宠 取消 백수", service.subscribe(group, "member", "백수"))
            assertEquals(1, queries)
            latest = snapshot(101)
            clock.advance(Duration.ofMinutes(2))
            assertEquals(1, service.tick())
            assertEquals(2, queries)
            assertEquals(1, sends)
        }
    }

    @Test fun `polling intervals follow activity boundaries and handle unknown or future time`() {
        listOf(Duration.ZERO to 2L, Duration.ofHours(2) to 10L, Duration.ofDays(1) to 30L,
            Duration.ofDays(7) to 120L, Duration.ofDays(30) to 360L).forEach { (age, minutes) ->
            assertEquals(minutes, PetPollingPolicy.interval(clock.millis() - age.toMillis(), clock.millis()).toMinutes())
        }
        assertEquals(360, PetPollingPolicy.interval(null, clock.millis()).toMinutes())
        assertEquals(2, PetPollingPolicy.interval(clock.millis() + 1000, clock.millis()).toMinutes())
        assertEquals(360, PetPollingPolicy.retry(20).toMinutes())
    }

    @Test fun `baseline is silent and only latest updated game is delivered once across restart`() = runBlocking {
        val repository = repo()
        var latest = snapshot()
        var sends = 0
        val rendered = mutableListOf<Long>()
        fun service() = GroupPetService(repo(), { latest }, {
            rendered += it.latest!!.id; "match".toText()
        }, { _, _ -> sends++ }, clock, permission = { grantedPermission() })
        val first = service()
        first.subscribe(group, "member", "백수")
        assertEquals(0L, repository.pushCount(), "neither subscribing nor polling an unchanged baseline is a push")
        assertEquals(0, first.tick())
        clock.advance(Duration.ofMinutes(2))
        assertEquals(0, first.tick())
        latest = snapshot(103)
        clock.advance(Duration.ofMinutes(2))
        assertEquals(1, first.tick())
        assertEquals(listOf(103L), rendered)
        assertEquals(103, repository.list(group).single().lastMatchId)
        clock.advance(Duration.ofMinutes(2))
        assertEquals(0, service().tick())
        latest = snapshot(101) // Delayed/older upstream response must not roll the cursor back.
        clock.advance(Duration.ofMinutes(2))
        assertEquals(0, service().tick())
        assertEquals(1, sends)
        assertEquals(103, repository.list(group).single().lastMatchId)
        assertEquals(1L, repo().pushCount(7, group), "a restart must not reset statistics or count an unchanged match")
        assertTrue(first.remove(group, "백수"))
        assertEquals(1L, repo().pushCount(7, group), "unsubscribe must retain lifetime totals")
    }

    @Test fun `same player is fetched once and failed group retries without duplicating successful group`() = runBlocking {
        val repository = repo()
        var latest = snapshot()
        var fetches = 0
        var renders = 0
        var failFirst = true
        val sends = mutableListOf<String>()
        val service = GroupPetService(repository, { fetches++; latest }, { renders++; "match".toText() }, { target, _ ->
            if (target == group && failFirst) throw java.net.SocketTimeoutException("simulated timeout")
            sends += target
        }, clock, permission = { grantedPermission() })
        service.subscribe(group, "member", "백수")
        service.subscribe(otherGroup, "member", "백수")
        fetches = 0
        latest = snapshot(101)
        clock.advance(Duration.ofMinutes(2))
        assertEquals(1, service.tick())
        assertEquals(1, fetches)
        assertEquals(0L, repository.pushCount(7, group), "timeout is not a confirmed success")
        assertEquals(1L, repository.pushCount(7, otherGroup))
        assertEquals(1, renders)
        assertEquals(100, repository.list(group).single().lastMatchId)
        assertEquals(1, repository.list(group).single().failures)
        assertEquals(101, repository.list(otherGroup).single().lastMatchId)
        failFirst = false
        clock.advance(Duration.ofMinutes(5))
        assertEquals(1, service.tick())
        assertEquals(listOf(otherGroup, group), sends)
        assertEquals(0, repository.list(group).single().failures)
        assertEquals(2L, repository.pushCount(7))
        assertEquals(1L, repository.pushCount(7, group))
        assertEquals(1L, repository.pushCount(7, otherGroup))
    }

    @Test fun `counter outage cannot make an acknowledged match notification retry`() = runBlocking<Unit> {
        val repository = repo()
        repository.add(group, "member", snapshot(), clock.millis())
        assertEquals(0L, repository.pushCount())
        java.sql.DriverManager.getConnection("jdbc:sqlite:${root.resolve("state.db")}").use { c -> c.createStatement().use {
            it.execute("CREATE TRIGGER fail_push_count BEFORE INSERT ON qq_push_receipts BEGIN SELECT RAISE(ABORT,'fixture counter outage'); END")
        } }
        var sends = 0
        val service = GroupPetService(repository, { snapshot(101) }, { "image".toText() },
            { _,_ -> sends++ }, clock, permission = { grantedPermission() })
        clock.advance(Duration.ofMinutes(2))
        assertEquals(1, service.tick())
        clock.advance(Duration.ofMinutes(2))
        assertEquals(0, service.tick())
        assertEquals(1, sends)
        assertEquals(101L, repository.list(group).single().lastMatchId)
        assertEquals(0L, repository.pushCount())
    }

    @Test fun `no history is cold but first new match is delivered`() = runBlocking {
        val repository = repo()
        var latest = snapshot(null)
        val service = GroupPetService(repository, { latest }, { "match".toText() }, { _, _ -> }, clock, permission = { grantedPermission() })
        service.subscribe(group, "member", "백수")
        assertEquals(clock.millis() + Duration.ofHours(6).toMillis(), repository.list(group).single().nextCheckAt)
        latest = snapshot(1)
        clock.advance(Duration.ofHours(6))
        assertEquals(1, service.tick())
    }

    @Test fun `unchanged player cools down and query errors preserve delivery cursor`() = runBlocking {
        val repository = repo()
        val latest = snapshot()
        var fail = false
        val service = GroupPetService(repository, { if (fail) error("network") else latest },
            { "match".toText() }, { _, _ -> error("unchanged") }, clock, permission = { grantedPermission() })
        service.subscribe(group, "member", "백수")
        clock.advance(Duration.ofDays(2))
        service.tick()
        assertEquals(clock.millis() + Duration.ofMinutes(30).toMillis(), repository.list(group).single().nextCheckAt)
        fail = true
        clock.advance(Duration.ofMinutes(30))
        service.tick()
        val failed = repository.list(group).single()
        assertEquals(100, failed.lastMatchId)
        assertEquals(1, failed.failures)
        assertEquals(clock.millis() + Duration.ofMinutes(30).toMillis(), failed.nextCheckAt,
            "failure must not make a cold player poll more frequently")
    }

    @Test fun `cancel during render prevents sending and only removes current group's subscription`() = runBlocking {
        val repository = repo()
        var latest = snapshot()
        lateinit var service: GroupPetService
        service = GroupPetService(repository, { latest }, {
            assertTrue(service.remove(group, "백수")); "match".toText()
        }, { _, _ -> error("cancelled subscription must not send") }, clock, permission = { grantedPermission() })
        service.subscribe(group, "member", "백수")
        latest = snapshot(101)
        clock.advance(Duration.ofMinutes(2))
        assertEquals(0, service.tick())
        assertTrue(repository.list(group).isEmpty())
        repository.add(otherGroup, "member", latest, clock.millis())
        assertFalse(service.remove(group, "백수"))
        assertEquals(1, repository.list(otherGroup).size)
    }

    @Test fun `identity changes never deliver another player's games`() = runBlocking {
        val repository = repo()
        var latest = snapshot()
        val service = GroupPetService(repository, { latest }, { error("identity") }, { _, _ -> error("identity") }, clock, permission = { grantedPermission() })
        service.subscribe(group, "member", "백수")
        latest = snapshot(101).copy(playerId = 8)
        clock.advance(Duration.ofMinutes(2))
        assertEquals(0, service.tick())
        assertEquals(1, repository.list(group).single().failures)
    }

    @Test fun `persistent leases exclude a second worker and expire after a crash`() {
        val first = repo()
        val second = repo()
        first.add(group, "member", snapshot(), clock.millis())
        clock.advance(Duration.ofMinutes(2))
        val row = first.due(clock.millis()).single()
        assertTrue(first.claim(row, "first", clock.millis()))
        assertFalse(second.claim(row, "second", clock.millis()))
        assertTrue(second.due(clock.millis()).isEmpty())
        clock.advance(Duration.ofMinutes(5))
        assertTrue(second.claim(row, "second", clock.millis()))
        assertFalse(first.owns(row, "first", clock.millis()))
        assertEquals(0, first.finish(row.copy(lastMatchId = 999), "first"))
        assertEquals(100, second.list(group).single().lastMatchId)
    }

    @Test fun `group cap rolls back failed add and duplicate never resets baseline`() {
        val repository = repo()
        repeat(3) { repository.add(group, "member", snapshot().copy(playerId = it + 1L), clock.millis()) }
        assertFailsWith<IllegalArgumentException> {
            repository.add(group, "member", snapshot().copy(playerId = 11), clock.millis())
        }
        assertEquals(3, repo().list(group).size)
        assertFalse(repository.add(group, "member", snapshot(999).copy(playerId = 1), clock.millis()))
        assertEquals(100, repository.list(group).first { it.playerId == 1L }.lastMatchId)
        assertTrue(repository.remove(group, 1))
        assertTrue(repository.add(group, "member", snapshot().copy(playerId = 11), clock.millis()))
        assertEquals(3, repository.list(group).size)
    }
}

internal class MutableClock(private var time: Instant = Instant.parse("2026-10-22T06:00:00Z")) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = this
    override fun instant(): Instant = time
    fun advance(duration: Duration) { time = time.plus(duration) }
}
