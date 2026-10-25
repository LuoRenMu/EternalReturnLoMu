package cn.luorenmu.plugins.bililive

import cn.luorenmu.common.database.sqliteConnection
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.io.TempDir
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.*

private typealias LiveConnection = ((Connection) -> Unit) -> Unit

class BiliLiveEntitlementTest {
    @TempDir lateinit var root: Path
    private val user = "A".repeat(32)
    private val otherUser = "B".repeat(32)
    private fun room(id: Long) = BiliRoom(id, "title", 0, "")

    private fun fixture(postgres: Boolean, block: (LiveConnection) -> Unit) {
        if (!postgres) {
            block { action -> sqliteConnection(root.resolve("live.db")).use(action) }
            return
        }
        val url = System.getenv("GROUP_PET_PG_TEST_URL")
        assumeTrue(!url.isNullOrBlank(), "Requires isolated PostgreSQL fixture")
        val schema = "live_claim_" + UUID.randomUUID().toString().replace("-", "")
        DriverManager.getConnection(url).use { c -> c.createStatement().use { it.execute("CREATE SCHEMA $schema") } }
        try {
            block { action -> DriverManager.getConnection(url).use { c -> c.schema = schema; action(c) } }
        } finally {
            check(schema.matches(Regex("live_claim_[a-f0-9]{32}")))
            DriverManager.getConnection(url).use { c -> c.createStatement().use { it.execute("DROP SCHEMA $schema CASCADE") } }
        }
    }

    private fun verifyPolicy(connection: LiveConnection) {
        val repo = BiliLiveRepository(connection)
        assertFalse(repo.claim("elsewhere", user))
        assertFalse(repo.claim(LIVE_CLAIM_GROUP, "invalid-sender"))
        assertFalse(repo.hasClaim(user))
        assertEquals(LiveSubscriptionResult.NOT_CLAIMED, repo.add("g1", user, room(1)))
        assertTrue(repo.claim(LIVE_CLAIM_GROUP, user))
        assertTrue(repo.claim(LIVE_CLAIM_GROUP, user))
        assertTrue(repo.claim("73DE7E7179ABA7BB8A6E83AEAC89D454", otherUser))
        assertTrue(BiliLiveRepository(connection).hasClaim(user))
        assertEquals(LiveSubscriptionResult.ADDED, repo.add("g1", user, room(1)))
        assertEquals(LiveSubscriptionResult.ALREADY_SUBSCRIBED, repo.add("g1", user, room(1)))
        assertEquals(LiveSubscriptionResult.GROUP_LIMIT, repo.add("g1", otherUser, room(2)))
        assertEquals(LiveSubscriptionResult.USER_LIMIT, repo.add("g2", user, room(2)))
        assertEquals(LiveSubscriptionResult.ADDED, repo.add("g2", user, room(1)), "same user and same room in multiple groups is allowed")
        assertEquals(LiveSubscriptionResult.ADDED, repo.add("g3", otherUser, room(2)))
        assertFalse(repo.remove("g1", 2), "cannot release somebody else's group/room binding")
        assertEquals(LiveSubscriptionResult.GROUP_LIMIT, repo.add("g1", otherUser, room(2)))
        assertTrue(repo.remove("g1", 1))
        assertEquals(LiveSubscriptionResult.USER_LIMIT, repo.add("g4", user, room(2)), "g2 still holds the user's room slot")
        assertTrue(repo.remove("g2", 1))
        assertEquals(LiveSubscriptionResult.ADDED, repo.add("g4", user, room(2)), "cancelling the last old room frees the user slot")
        assertTrue(repo.hasClaim(user), "cancellation never revokes the durable entitlement")
        assertEquals(2, repo.activeRows().size)
        assertEquals(2, repo.rows().size, "failed policy checks cannot leave orphan rows")
    }

    private fun <T> race(tasks: List<() -> T>): List<T> {
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(tasks.size)
        try {
            val futures = tasks.map { task -> pool.submit<T> { check(start.await(10, TimeUnit.SECONDS)); task() } }
            start.countDown()
            return futures.map { it.get(30, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS))
        }
    }

    private fun verifyConcurrency(connection: LiveConnection) {
        val repo = BiliLiveRepository(connection)
        val users = (0..7).map { it.toString(16).padStart(32, '0') }
        users.forEach { assertTrue(repo.claim(LIVE_CLAIM_GROUP, it)) }
        val groupRace = race(users.mapIndexed { index, id -> { BiliLiveRepository(connection).add("group", id, room(index + 1L)) } })
        assertEquals(1, groupRace.count { it == LiveSubscriptionResult.ADDED })
        assertEquals(7, groupRace.count { it == LiveSubscriptionResult.GROUP_LIMIT })
        repo.activeRows().forEach { repo.remove(it.group, it.room) }
        val userRace = race(users.indices.map { index -> { BiliLiveRepository(connection).add("g$index", users[0], room(index + 1L)) } })
        assertEquals(1, userRace.count { it == LiveSubscriptionResult.ADDED })
        assertEquals(7, userRace.count { it == LiveSubscriptionResult.USER_LIMIT })
        repo.activeRows().forEach { repo.remove(it.group, it.room) }
        val sameRoom = race(users.indices.map { index -> { BiliLiveRepository(connection).add("g$index", users[0], room(1)) } })
        assertTrue(sameRoom.all { it == LiveSubscriptionResult.ADDED })
        assertEquals(8, repo.activeRows().size)
        repo.activeRows().forEach { repo.remove(it.group, it.room) }
        val duplicates = race(users.indices.map { { BiliLiveRepository(connection).add("group", users[0], room(1)) } })
        assertEquals(1, duplicates.count { it == LiveSubscriptionResult.ADDED })
        assertEquals(7, duplicates.count { it == LiveSubscriptionResult.ALREADY_SUBSCRIBED })
        assertEquals(1, repo.activeRows().size)
        assertEquals(1, repo.rows().size)
    }

    private fun verifyRollback(connection: LiveConnection) {
        val repo = BiliLiveRepository(connection)
        assertTrue(repo.claim(LIVE_CLAIM_GROUP, user))
        // Fail after writing the live row but before its authorization binding is inserted.
        val broken = BiliLiveRepository { action -> connection { c ->
            val wrapped = Proxy.newProxyInstance(Connection::class.java.classLoader, arrayOf(Connection::class.java)) { _, method, args ->
                if (method.name == "prepareStatement" && args?.firstOrNull().toString().uppercase().let {
                    it.startsWith("INSERT") && it.contains("BILI_LIVE_BINDINGS") }) throw SQLException("fixture binding failure")
                try { method.invoke(c, *(args ?: emptyArray())) } catch (e: InvocationTargetException) { throw e.targetException }
            } as Connection
            action(wrapped)
            assertTrue(c.autoCommit, "transaction must restore the borrowed host connection")
        } }
        val failed = assertFails { broken.add("group", user, room(1)) }
        assertTrue(generateSequence(failed) { it.cause }.any { it.message.orEmpty().contains("fixture binding failure") })
        assertTrue(repo.rows().isEmpty(), "a failed authorization binding must roll back the live row too")
        assertTrue(repo.activeRows().isEmpty())
        assertTrue(repo.hasClaim(user))
        assertEquals(LiveSubscriptionResult.ADDED, repo.add("group", user, room(2)))
    }

    @Test fun `sqlite claims and one room per group and user are durable`() = fixture(false, ::verifyPolicy)
    @Test fun `postgres claims and one room per group and user are durable`() = fixture(true, ::verifyPolicy)
    @Test fun `sqlite limits hold across simultaneous repository instances`() = fixture(false, ::verifyConcurrency)
    @Test fun `postgres limits hold across simultaneous repository instances`() = fixture(true, ::verifyConcurrency)
    @Test fun `sqlite failed binding rolls back all subscription writes`() = fixture(false, ::verifyRollback)
    @Test fun `postgres failed binding rolls back all subscription writes`() = fixture(true, ::verifyRollback)

    private fun verifyEndedClaims(connection: LiveConnection) {
        val repo = BiliLiveRepository(connection)
        repo.claim(LIVE_CLAIM_GROUP, user)
        val live = room(1).copy(status = 1, session = "2026-10-23 06:35:00")
        assertEquals(LiveSubscriptionResult.ADDED, repo.add("group", user, live))
        val old = repo.rows().single()
        val offline = live.copy(status = 0, session = "")
        val claims = race(List(8) { { BiliLiveRepository(connection).advance(old, offline) } })
        assertEquals(1, claims.count { it }, "the cursor and end claim are atomic across workers")
        assertEquals(0, repo.rows().single().status)
        assertEquals(live.session, repo.rows().single().session)
        assertTrue(repo.advance(repo.rows().single(), live))
        assertFalse(BiliLiveRepository(connection).advance(repo.rows().single(), offline.copy(status = 2)),
            "the same known session cannot announce another end after a restart and status flap")
        assertEquals(2, repo.rows().single().status, "duplicate suppression must still advance the observed status")
        val next = live.copy(session = "2026-10-23 10:00:00")
        assertTrue(repo.advance(repo.rows().single(), next))
        assertTrue(repo.advance(repo.rows().single(), offline), "a different session is entitled to its own end notice")
    }

    private fun verifyEndClaimRollback(connection: LiveConnection) {
        val repo = BiliLiveRepository(connection)
        repo.claim(LIVE_CLAIM_GROUP, user)
        val live = room(1).copy(status = 1, session = "session-1")
        repo.add("group", user, live)
        val old = repo.rows().single()
        val broken = BiliLiveRepository { action -> connection { c ->
            val wrapped = Proxy.newProxyInstance(Connection::class.java.classLoader, arrayOf(Connection::class.java)) { _, method, args ->
                if (method.name == "prepareStatement" && args?.firstOrNull().toString().uppercase().let {
                    it.startsWith("INSERT") && it.contains("BILI_LIVE_ENDED") }) throw SQLException("fixture end claim failure")
                try { method.invoke(c, *(args ?: emptyArray())) } catch (e: InvocationTargetException) { throw e.targetException }
            } as Connection
            try { action(wrapped) } finally { assertTrue(c.autoCommit) }
        } }
        val failed = assertFails { broken.advance(old, live.copy(status = 0, session = "")) }
        assertTrue(generateSequence(failed) { it.cause }.any { it.message.orEmpty().contains("fixture end claim failure") })
        assertEquals(old, repo.rows().single(), "end bookkeeping failure cannot consume the live cursor")
        assertTrue(repo.advance(old, live.copy(status = 0, session = "")))
    }
    @Test fun `sqlite end claims are unique per known session and atomic across workers`() = fixture(false, ::verifyEndedClaims)
    @Test fun `postgres end claims are unique per known session and atomic across workers`() = fixture(true, ::verifyEndedClaims)
    @Test fun `sqlite failed end claim rolls back the cursor`() = fixture(false, ::verifyEndClaimRollback)
    @Test fun `postgres failed end claim rolls back the cursor`() = fixture(true, ::verifyEndClaimRollback)

    private fun verifyGlobalCap(connection: LiveConnection) {
        val repo = BiliLiveRepository(connection)
        for (id in 1L..50L) {
            val claimant = id.toString(16).padStart(32, '0')
            assertTrue(repo.claim(LIVE_CLAIM_GROUP, claimant))
            assertEquals(LiveSubscriptionResult.ADDED, repo.add("group$id", claimant, room(id)))
        }
        assertTrue(repo.claim(LIVE_CLAIM_GROUP, user))
        assertEquals(LiveSubscriptionResult.TOTAL_LIMIT, repo.add("over", user, room(51)))
        assertEquals(LiveSubscriptionResult.ADDED, repo.add("shared", user, room(1)), "an existing room does not consume another global room slot")
        assertEquals(51, repo.activeRows().size)
        assertTrue(repo.remove("shared", 1))
        assertTrue(repo.remove("group50", 50))
        assertEquals(LiveSubscriptionResult.ADDED, repo.add("over", user, room(51)))
    }
    @Test fun `sqlite global cap counts distinct active rooms and permits shared rooms`() = fixture(false, ::verifyGlobalCap)
    @Test fun `postgres global cap counts distinct active rooms and permits shared rooms`() = fixture(true, ::verifyGlobalCap)

    @Test fun `unbound legacy subscriptions never poll until a claimed owner chooses one without losing old cursors`() = fixture(false) { connection ->
        val repo = BiliLiveRepository(connection)
        repo.rows() // Initialize the independent schema before seeding the previous version's records.
        connection { c -> c.createStatement().use {
            it.execute("INSERT INTO test_bili_live VALUES('group',1,0,'old-sent'),('group',2,0,'other-sent')")
        } }
        var fetches = 0
        val sends = mutableListOf<Long>()
        val monitor = BiliLiveMonitor(repo, { id -> fetches++; room(id).copy(status = 1, session = "new-live") },
            { _, notice -> sends += notice.room.id }, permission = { true }, pause = {})
        runBlocking {
            monitor.tick()
            assertEquals(0, fetches)
            assertEquals(LIVE_CLAIM_NOTICE, monitor.command("group", user, "订阅", "1"))
            assertEquals(0, fetches)
            assertEquals(LIVE_CLAIM_SUCCESS, monitor.claim(LIVE_CLAIM_GROUP, user))
            assertContains(monitor.command("group", user, "列表", ""), "历史订阅未绑定")
            assertContains(monitor.command("group", user, "订阅", "1"), "已监听")
            assertEquals("old-sent", repo.rows().first { it.room == 1L }.session, "rebinding must not reset the durable sent cursor")
            assertEquals(2, repo.rows().size, "do not destructively delete other legacy data")
            assertEquals(1, repo.activeRows().size)
            monitor.tick()
            assertEquals(listOf(1L), sends)
            assertEquals("other-sent", repo.rows().first { it.room == 2L }.session)
        }
    }
}
