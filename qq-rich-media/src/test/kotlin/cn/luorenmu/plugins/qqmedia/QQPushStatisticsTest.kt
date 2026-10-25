package cn.luorenmu.plugins.qqmedia

import cn.luorenmu.common.database.sqliteConnection
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.io.TempDir
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

private typealias PushConnection = ((Connection) -> Unit) -> Unit

class QQPushStatisticsTest {
    @TempDir lateinit var root: Path

    private fun fixture(postgres: Boolean, block: (PushConnection) -> Unit) {
        if (!postgres) {
            block { action -> sqliteConnection(root.resolve("push.db")).use(action) }
            return
        }
        val url = System.getenv("GROUP_PET_PG_TEST_URL")
        assumeTrue(!url.isNullOrBlank(), "Requires isolated PostgreSQL fixture")
        val schema = "push_stats_" + UUID.randomUUID().toString().replace("-", "")
        DriverManager.getConnection(url).use { c -> c.createStatement().use { it.execute("CREATE SCHEMA $schema") } }
        try {
            block { action -> DriverManager.getConnection(url).use { c -> c.schema = schema; action(c) } }
        } finally {
            check(schema.matches(Regex("push_stats_[a-f0-9]{32}")))
            DriverManager.getConnection(url).use { c -> c.createStatement().use { it.execute("DROP SCHEMA $schema CASCADE") } }
        }
    }

    private fun verifyCounts(connection: PushConnection) {
        val store = QQPushStatistics(connection)
        assertEquals(0L, store.count(QQPushKind.BILI_LIVE))
        assertTrue(store.record("live-1", QQPushKind.BILI_LIVE, "g1", 5_000_000_000, 100))
        assertFalse(store.record("live-1", QQPushKind.BILI_LIVE, "g1", 5_000_000_000, 100))
        assertTrue(store.record("live-2", QQPushKind.BILI_LIVE, "g2", 5_000_000_000, 101))
        assertTrue(store.record("live-3", QQPushKind.BILI_LIVE, "g1", 7, 102))
        assertTrue(store.record("pet-1", QQPushKind.GROUP_PET, "g1", 5_000_000_000, 103))
        assertEquals(3L, store.count(QQPushKind.BILI_LIVE))
        assertEquals(2L, store.count(QQPushKind.BILI_LIVE, 5_000_000_000))
        assertEquals(1L, store.count(QQPushKind.BILI_LIVE, 5_000_000_000, "g1"))
        assertEquals(2L, store.count(QQPushKind.BILI_LIVE, group = "g1"))
        assertEquals(1L, QQPushStatistics(connection).count(QQPushKind.GROUP_PET))
        assertEquals(0L, store.count(QQPushKind.GROUP_PET, group = "g2"))
        connection { c -> assertTrue(c.autoCommit); assertFalse(c.isClosed) }
    }

    private fun verifyConcurrency(connection: PushConnection) {
        val stores = List(4) { QQPushStatistics(connection) }
        val pool = Executors.newFixedThreadPool(8)
        try {
            val unique = pool.invokeAll((0..31).map { index -> Callable {
                stores[index % stores.size].record("send-$index", QQPushKind.GROUP_PET, "group", 7, index.toLong())
            } }).map { it.get(30, TimeUnit.SECONDS) }
            assertTrue(unique.all { it })
            val same = pool.invokeAll((0..15).map { index -> Callable {
                stores[index % stores.size].record("one-receipt", QQPushKind.GROUP_PET, "group", 7, 100)
            } }).map { it.get(30, TimeUnit.SECONDS) }
            assertEquals(1, same.count { it })
            assertEquals(33L, QQPushStatistics(connection).count(QQPushKind.GROUP_PET, 7, "group"))
        } finally { pool.shutdownNow(); assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS)) }
    }

    private fun wrapped(connection: PushConnection, intercept: (Connection, java.lang.reflect.Method, Array<out Any?>?) -> Any?): PushConnection = { action ->
        connection { c ->
            val wrapper = Proxy.newProxyInstance(Connection::class.java.classLoader, arrayOf(Connection::class.java)) { _, method, args ->
                intercept(c, method, args)
            } as Connection
            try { action(wrapper) } finally { assertTrue(c.autoCommit) }
        }
    }
    private fun invoke(c: Connection, method: java.lang.reflect.Method, args: Array<out Any?>?): Any? =
        try { method.invoke(c, *(args ?: emptyArray())) } catch (e: InvocationTargetException) { throw e.targetException }

    private fun verifyRollback(connection: PushConnection) {
        val normal = QQPushStatistics(connection)
        normal.count(QQPushKind.BILI_LIVE)
        val broken = QQPushStatistics(wrapped(connection) { c, method, args ->
            if (method.name == "prepareStatement" && args?.firstOrNull().toString().uppercase().let {
                it.startsWith("UPDATE") && it.contains("QQ_PUSH_STATISTICS") }) throw SQLException("fixture counter failure")
            invoke(c, method, args)
        })
        val failure = assertFails { broken.record("rollback", QQPushKind.BILI_LIVE, "group", 1, 100) }
        assertTrue(generateSequence(failure) { it.cause }.any { it.message.orEmpty().contains("fixture counter failure") })
        assertEquals(0L, normal.count(QQPushKind.BILI_LIVE))
        assertTrue(normal.record("rollback", QQPushKind.BILI_LIVE, "group", 1, 100), "receipt must roll back together with the counter")
        assertEquals(1L, normal.count(QQPushKind.BILI_LIVE))
    }

    private fun verifyLostCommit(connection: PushConnection) {
        val fail = AtomicBoolean(false)
        val commits = AtomicInteger()
        val store = QQPushStatistics(wrapped(connection) { c, method, args ->
            val result = invoke(c, method, args)
            if (method.name == "commit" && fail.compareAndSet(true, false)) {
                commits.incrementAndGet()
                throw SQLException("fixture lost commit acknowledgement")
            }
            result
        })
        assertEquals(0L, store.count(QQPushKind.BILI_LIVE)) // initialize before arming the failure
        fail.set(true)
        assertTrue(store.recordSuccess(QQPushKind.BILI_LIVE, "group", 1, 100))
        assertEquals(1, commits.get())
        assertEquals(1L, QQPushStatistics(connection).count(QQPushKind.BILI_LIVE), "retry after an uncertain commit must not double count")
    }

    @Test fun `sqlite durable counts separate kinds subjects and groups`() = fixture(false, ::verifyCounts)
    @Test fun `postgres durable counts separate kinds subjects and groups`() = fixture(true, ::verifyCounts)
    @Test fun `sqlite concurrent increments and repeated receipts never lose or duplicate counts`() = fixture(false, ::verifyConcurrency)
    @Test fun `postgres concurrent increments and repeated receipts never lose or duplicate counts`() = fixture(true, ::verifyConcurrency)
    @Test fun `sqlite counter and receipt roll back together`() = fixture(false, ::verifyRollback)
    @Test fun `postgres counter and receipt roll back together`() = fixture(true, ::verifyRollback)
    @Test fun `sqlite retry after lost commit acknowledgement counts once`() = fixture(false, ::verifyLostCommit)
    @Test fun `postgres retry after lost commit acknowledgement counts once`() = fixture(true, ::verifyLostCommit)

    @Test fun `statistics outage is bounded non throwing and does not fabricate any successful record`() = fixture(false) { connection ->
        var attempts = 0
        val broken = QQPushStatistics(wrapped(connection) { c, method, args ->
            if (method.name == "prepareStatement" && args?.firstOrNull().toString().uppercase().let {
                it.startsWith("INSERT") && it.contains("QQ_PUSH_RECEIPTS") }) {
                attempts++
                throw SQLException("fixture unavailable")
            }
            invoke(c, method, args)
        })
        assertFalse(broken.recordSuccess(QQPushKind.GROUP_PET, "group", 7, 100))
        assertEquals(3, attempts)
        assertEquals(0L, QQPushStatistics(connection).count(QQPushKind.GROUP_PET))
    }
}
