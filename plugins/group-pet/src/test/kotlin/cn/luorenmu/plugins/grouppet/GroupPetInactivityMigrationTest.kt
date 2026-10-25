package cn.luorenmu.plugins.grouppet

import cn.luorenmu.common.database.schemaScript
import cn.luorenmu.common.database.sqliteConnection
import cn.luorenmu.common.database.writeTransaction
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.*

class GroupPetInactivityMigrationTest {
    @TempDir lateinit var root: Path
    private val now = 1_792_664_400_000L
    private val gameId = 5_000_000_100L

    private fun legacy(path: Path) {
        val schema = checkNotNull(javaClass.getResourceAsStream("/sql/group-pet-sqlite.sql"))
            .bufferedReader().use { it.readText() }.replace(", inactivity_match_id BIGINT", "")
            .replace("expiry_notice_next_at BIGINT NOT NULL DEFAULT 0,", "")
        assertFalse(schema.contains("inactivity_match_id"))
        assertFalse(schema.contains("expiry_notice_next_at"))
        sqliteConnection(path).use { c -> c.createStatement().use { sql ->
            schema.split(';').map(String::trim).filter(String::isNotEmpty).forEach(sql::execute)
            sql.execute("""INSERT INTO group_pet(group_id,player_id,nickname,requester_id,last_match_id,last_match_time,next_check_at,expires_at)
                VALUES ('group',5000000000,'백수','member',$gameId,${now - 259_200_000},$now,${now + 604_800_000})""")
        } }
    }

    @Test fun `a stale due snapshot cannot reacquire and repeat an already acknowledged inactivity notice`() {
        val repo = GroupPetRepository(root.resolve("stale.db"))
        repo.add("group", "member", PetSnapshot(7, "백수", PetMatch(gameId, now - 259_200_000, 41)), now)
        val due = now + 1_800_000
        val stale = repo.due(due).single()
        assertTrue(repo.claim(stale, "first", due))
        repo.finish(stale.copy(inactivityMatchId = gameId, nextCheckAt = due + 1_800_000), "first")
        assertFalse(repo.claim(stale, "late-worker", due + 1_800_000))
        val fresh = repo.due(due + 1_800_000).single()
        assertTrue(repo.claim(fresh, "fresh-worker", due + 1_800_000))
    }

    @Test fun `sqlite upgrade preserves cursors expiry bigint markers and old plugin compatibility`() {
        val path = root.resolve("legacy.db")
        legacy(path)
        val repo = GroupPetRepository(path)
        val row = repo.list("group").single()
        assertNull(row.inactivityMatchId)
        assertEquals(0L, row.expiryNoticeNextAt)
        assertEquals(gameId, row.lastMatchId)
        assertEquals(now + 604_800_000, row.expiresAt)
        assertTrue(repo.claim(row, "notifier", now))
        assertEquals(1, repo.finish(row.copy(inactivityMatchId = gameId), "notifier"))
        assertEquals(gameId, GroupPetRepository(path).list("group").single().inactivityMatchId)
        // A failed acknowledgement replay carrying an old null marker must not erase the durable one.
        val marked = repo.list("group").single()
        assertTrue(repo.claim(marked, "retry", now))
        repo.finish(marked.copy(inactivityMatchId = null), "retry")
        assertEquals(gameId, repo.list("group").single().inactivityMatchId)
        // Rolling back to an old plugin still allows its original-column reads and writes.
        sqliteConnection(path).use { c -> c.createStatement().use { sql ->
            sql.executeQuery("SELECT nickname,last_match_id,expires_at FROM group_pet").use { r ->
                assertTrue(r.next()); assertEquals("백수", r.getString(1))
                assertEquals(gameId, r.getLong(2)); assertEquals(row.expiresAt, r.getLong(3))
            }
            sql.executeUpdate("UPDATE group_pet SET last_error=NULL WHERE group_id='group'")
        } }
        assertEquals(gameId, repo.list("group").single().inactivityMatchId)
    }

    @Test fun `sqlite migration rolls back after a DDL failure and can be retried`() {
        val path = root.resolve("rollback.db")
        legacy(path)
        sqliteConnection(path).use { c ->
            assertFailsWith<IllegalStateException> {
                c.writeTransaction {
                    c.schemaScript(javaClass, "/sql/group-pet-add-inactivity.sql")
                    c.schemaScript(javaClass, "/sql/group-pet-add-expiry-notice.sql")
                    c.schemaScript(javaClass, "/sql/group-pet-expiry-index.sql")
                    error("injected failure after column creation")
                }
            }
            c.metaData.getColumns(null, null, "group_pet", "inactivity_match_id").use { assertFalse(it.next()) }
            c.metaData.getColumns(null, null, "group_pet", "expiry_notice_next_at").use { assertFalse(it.next()) }
            c.createStatement().use { sql -> sql.executeQuery("SELECT last_match_id FROM group_pet").use { r ->
                assertTrue(r.next()); assertEquals(gameId, r.getLong(1))
            } }
        }
        assertEquals(gameId, GroupPetRepository(path).list("group").single().lastMatchId)
    }

    @Test fun `two repository instances can upgrade the same old sqlite database concurrently`() {
        val path = root.resolve("concurrent.db")
        legacy(path)
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val futures = (1..2).map { pool.submit<PetSubscription> {
                start.await(); GroupPetRepository(path).list("group").single()
            } }
            start.countDown()
            futures.forEach { assertEquals(gameId, it.get(10, TimeUnit.SECONDS).lastMatchId) }
        } finally { pool.shutdownNow() }
        sqliteConnection(path).use { c ->
            c.metaData.getColumns(null, null, "group_pet", "inactivity_match_id").use { r ->
                assertTrue(r.next()); assertFalse(r.next())
            }
        }
    }
}
