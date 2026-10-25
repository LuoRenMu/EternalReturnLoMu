package cn.luorenmu.plugins.grouppet

import cn.luorenmu.common.util.DatabaseBackend
import cn.luorenmu.common.util.DatabaseManager
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.time.Duration
import kotlin.test.*

class GroupPetPostgresTest {
    @TempDir lateinit var root: Path
    private val now = 1_792_664_400_000L
    private fun snapshot(id: Long = 7) = PetSnapshot(id, "player$id", PetMatch(66_829_052, now - 1000, 41))

    private fun fixture(block: (DatabaseManager, String, String) -> Unit) {
        val url = System.getenv("GROUP_PET_PG_TEST_URL")
        assumeTrue(!url.isNullOrBlank(), "Requires isolated PostgreSQL fixture")
        val schema = "grouppet_test_" + UUID.randomUUID().toString().replace("-", "")
        DriverManager.getConnection(url).use { c -> c.createStatement().use { it.execute("CREATE SCHEMA $schema") } }
        val manager = DatabaseManager(DatabaseBackend.POSTGRESQL, root.resolve("fallback.db"), {
            HikariDataSource(HikariConfig().apply {
                jdbcUrl = url + (if (url!!.contains('?')) "&" else "?") + "currentSchema=$schema"
                maximumPoolSize = 8
                minimumIdle = 0
            })
        })
        try {
            manager.initialize()
            assertEquals(DatabaseBackend.POSTGRESQL, manager.backend)
            block(manager, url!!, schema)
            assertFalse(Files.exists(root.resolve("fallback.db")))
        } finally {
            manager.close()
            check(schema.matches(Regex("grouppet_test_[a-f0-9]{32}")))
            DriverManager.getConnection(url).use { c -> c.createStatement().use { it.execute("DROP SCHEMA $schema CASCADE") } }
        }
    }

    @Test fun `sqlite host keeps using existing sqlite subscriptions`() {
        val path = root.resolve("legacy.db")
        val expectedSource = GroupPetRepository(path)
        expectedSource.add("group", "member", snapshot(), now)
        val expected = expectedSource.list("group").single()
        val manager = DatabaseManager(DatabaseBackend.SQLITE, root.resolve("main.db"))
        try {
            assertEquals(expected, GroupPetRepository(path, manager).list("group").single())
            assertEquals(DatabaseBackend.SQLITE, manager.backend)
        } finally { manager.close() }
    }

    @Test fun `postgres imports sqlite reminder markers and pending expiry retry without rewriting the source`() = fixture { manager, _, _ ->
        val path = root.resolve("inactivity.db")
        val sqlite = GroupPetRepository(path)
        sqlite.add("group", "member", snapshot(), now)
        val row = sqlite.list("group").single()
        assertTrue(sqlite.claim(row, "notifier", now + 120_000))
        sqlite.finish(row.copy(inactivityMatchId = row.lastMatchId), "notifier")
        val marked = sqlite.list("group").single()
        assertTrue(sqlite.claimExpiry(marked, "expiry", row.expiresAt))
        sqlite.deferExpiry(marked, "expiry", row.expiresAt + 300_000, 1, "到期提醒：IOException")
        val bytes = Files.readAllBytes(path)
        val postgres = GroupPetRepository(path, manager)
        assertEquals(row.lastMatchId, postgres.list("group").single().inactivityMatchId)
        assertEquals(row.expiresAt, postgres.list("group").single().expiresAt)
        assertEquals(row.expiresAt + 300_000, postgres.list("group").single().expiryNoticeNextAt)
        assertContentEquals(bytes, Files.readAllBytes(path))
        assertEquals(row.lastMatchId, GroupPetRepository(path, manager).list("group").single().inactivityMatchId)
    }

    @Test fun `postgres expiry queue persists delays guards renewal and completes once`() = fixture { manager, _, _ ->
        val path = root.resolve("none-expiry.db")
        val repo = GroupPetRepository(path, manager)
        repo.add("group", "member", snapshot(), now)
        val row = repo.list("group").single()
        val end = row.expiresAt
        assertTrue(repo.expiredDue(end - 1).isEmpty())
        val expired = repo.expiredDue(end).single()
        assertTrue(repo.claimExpiry(expired, "first", end))
        repo.deferExpiry(expired, "first", end + 300_000, 1, "retry")
        val restarted = GroupPetRepository(path, manager)
        assertTrue(restarted.expiredDue(end + 299_999).isEmpty())
        assertFalse(restarted.claimExpiry(expired, "stale", end + 300_000))
        val retry = restarted.expiredDue(end + 300_000).single()
        assertTrue(restarted.claimExpiry(retry, "retry", end + 300_000))
        assertTrue(restarted.ownsExpiry(retry, "retry", end + 300_000))
        assertTrue(restarted.completeExpiry(retry, "retry"))
        assertFalse(restarted.completeExpiry(retry, "retry"))
        assertTrue(restarted.expiredDue(end + 300_000).isEmpty())
        repo.add("group", "member", snapshot(), now)
        val old = repo.expiredDue(end).single()
        assertTrue(repo.claimExpiry(old, "old-owner", end))
        assertTrue(repo.add("group", "member", snapshot().copy(latest = PetMatch(999, end, 41)), end))
        assertFalse(repo.ownsExpiry(old, "old-owner", end))
        assertFalse(repo.completeExpiry(old, "old-owner"))
        assertEquals(0, repo.deferExpiry(old, "old-owner", end + 300_000, 1, "stale"))
        assertEquals(999L, repo.list("group").single().lastMatchId)
    }

    @Test fun `postgres old schema upgrade rolls back on failed import then preserves existing data on retry`() = fixture { manager, url, schema ->
        val ddl = checkNotNull(javaClass.getResourceAsStream("/sql/group-pet-postgresql.sql"))
            .bufferedReader().use { it.readText() }.replace(", inactivity_match_id BIGINT", "")
            .replace("expiry_notice_next_at BIGINT NOT NULL DEFAULT 0,", "")
        assertFalse(ddl.contains("inactivity_match_id"))
        manager.useConnection { c -> c.createStatement().use { sql ->
            ddl.split(';').map(String::trim).filter(String::isNotEmpty).forEach(sql::execute)
            sql.execute("""INSERT INTO group_pet(group_id,player_id,nickname,requester_id,last_match_id,last_match_time,next_check_at,expires_at)
                VALUES ('existing',77,'existing','member',5000000001,$now,$now,${now + 604_800_000})""")
        } }
        val path = root.resolve("broken-old-inactivity.db")
        Class.forName("org.sqlite.JDBC")
        DriverManager.getConnection("jdbc:sqlite:$path").use { c -> c.createStatement().use { sql ->
            sql.execute("""CREATE TABLE group_pet(group_id TEXT,player_id INTEGER,nickname TEXT,requester_id TEXT,
                last_match_id INTEGER,last_match_time INTEGER,next_check_at INTEGER,failures INTEGER,last_error TEXT,expires_at INTEGER)""")
            sql.execute("INSERT INTO group_pet VALUES ('imported',88,NULL,'member',100,1,2,0,NULL,9999999999999)")
        } }
        val bytes = Files.readAllBytes(path)
        assertFails { GroupPetRepository(path, manager).list("existing") }
        assertContentEquals(bytes, Files.readAllBytes(path))
        manager.useConnection { c ->
            c.metaData.getColumns(null, schema, "group_pet", "inactivity_match_id").use { assertFalse(it.next()) }
            c.metaData.getColumns(null, schema, "group_pet", "expiry_notice_next_at").use { assertFalse(it.next()) }
            c.createStatement().use { sql -> sql.executeQuery("SELECT last_match_id FROM group_pet WHERE group_id='existing'").use { r ->
                assertTrue(r.next()); assertEquals(5_000_000_001L, r.getLong(1))
            } }
        }
        DriverManager.getConnection("jdbc:sqlite:$path").use { c -> c.createStatement().use {
            it.executeUpdate("UPDATE group_pet SET nickname='fixed' WHERE player_id=88")
        } }
        val repo = GroupPetRepository(path, manager)
        val row = repo.list("existing").single()
        assertEquals(now + 604_800_000, row.expiresAt)
        assertTrue(repo.claim(row, "notifier", now))
        repo.finish(row.copy(inactivityMatchId = row.lastMatchId), "notifier")
        assertEquals(5_000_000_001L, GroupPetRepository(path, manager).list("existing").single().inactivityMatchId)
        assertFalse(repo.claim(row, "stale-worker", now), "an old snapshot must not overwrite an acknowledged reminder")
        assertEquals("fixed", repo.list("imported").single().nickname)
        // The additive nullable column keeps the original old-plugin query valid after upgrade.
        DriverManager.getConnection(url).use { c -> c.createStatement().use { sql ->
            sql.executeQuery("SELECT nickname FROM $schema.group_pet WHERE group_id='existing'").use { r ->
                assertTrue(r.next()); assertEquals("existing", r.getString(1))
            }
        } }
    }

    @Test fun `postgres uses primary database persists bigint data and does not create sqlite`() = fixture { manager, _, _ ->
        val path = root.resolve("legacy.db")
        val first = GroupPetRepository(path, manager)
        assertTrue(first.add("group", "member", snapshot(5_000_000_000), now))
        val row = GroupPetRepository(path, manager).list("group").single()
        assertEquals(5_000_000_000L, row.playerId)
        assertEquals(now + Duration.ofDays(7).toMillis(), row.expiresAt)
        assertFalse(Files.exists(path))
        assertEquals(1, first.due(now + 120_000).size)
        assertTrue(first.claim(row, "first", now + 120_000))
        val second = GroupPetRepository(path, manager)
        assertFalse(second.claim(row, "second", now + 120_000))
        assertTrue(first.owns(row, "first", now + 120_001))
        assertEquals(1, first.finish(row.copy(lastMatchId = 66_829_053), "first"))
        assertEquals(66_829_053, second.list("group").single().lastMatchId)
        assertTrue(second.due(row.expiresAt).isEmpty())
        assertTrue(first.recordPush("group", row.playerId, now))
        assertEquals(1L, second.pushCount(row.playerId, "group"))
        assertTrue(second.remove("group", row.playerId))
        assertEquals(1L, GroupPetRepository(path, manager).pushCount(row.playerId), "PostgreSQL statistics outlive subscriptions")
        assertFalse(Files.exists(path), "recording statistics must not silently write SQLite when PostgreSQL is configured")
    }

    @Test fun `migration preserves subscriptions and cursors once without resurrecting deleted rows`() = fixture { manager, _, _ ->
        val path = root.resolve("legacy.db")
        val sqlite = GroupPetRepository(path)
        sqlite.add("group", "member", snapshot(), now)
        val expected = sqlite.list("group").single()
        assertTrue(sqlite.claim(expected, "old-worker", now + 120_000))
        val bytes = Files.readAllBytes(path)
        val postgres = GroupPetRepository(path, manager)
        assertEquals(expected, postgres.list("group").single())
        assertContentEquals(bytes, Files.readAllBytes(path))
        assertTrue(postgres.claim(expected, "new-worker", now + 120_000))
        assertTrue(postgres.remove("group", 7))
        assertTrue(GroupPetRepository(path, manager).list("group").isEmpty())
        assertEquals(expected, sqlite.list("group").single())
    }

    @Test fun `concurrent subscriptions across repository instances enforce three player cap`() = fixture { manager, _, _ ->
        val path = root.resolve("none.db")
        val pool = Executors.newFixedThreadPool(8)
        try {
            val results = pool.invokeAll((1L..10L).map { id -> Callable {
                runCatching { GroupPetRepository(path, manager).add("group", "member", snapshot(id), now) }.getOrDefault(false)
            } }).map { it.get() }
            assertEquals(3, results.count { it })
            val repository = GroupPetRepository(path, manager)
            assertEquals(3, repository.list("group").size)
            assertFalse(repository.add("group", "member", snapshot(repository.list("group").first().playerId), now))
            assertTrue(repository.add("other-group", "member", snapshot(), now))
            val row = repository.list("group").first()
            assertTrue(repository.claim(row, "owner", now + 120_000))
            assertFalse(repository.discard(row, "stale"))
            assertTrue(repository.discard(row, "owner"))
            assertEquals(2, repository.list("group").size)
            assertEquals(1, repository.list("other-group").size)
        } finally { pool.shutdownNow() }
    }

    @Test fun `failed migration rolls back all imported data and marker and can retry without changing sqlite`() = fixture { manager, url, schema ->
        val path = root.resolve("broken.db")
        Class.forName("org.sqlite.JDBC")
        DriverManager.getConnection("jdbc:sqlite:$path").use { c -> c.createStatement().use {
            it.execute("""CREATE TABLE group_pet (group_id TEXT,player_id INTEGER,nickname TEXT,requester_id TEXT,
                last_match_id INTEGER,last_match_time INTEGER,next_check_at INTEGER,failures INTEGER,
                last_error TEXT,expires_at INTEGER)""")
            it.execute("INSERT INTO group_pet VALUES ('group',7,'valid','member',100,1,2,0,NULL,9999999999999)")
            it.execute("INSERT INTO group_pet VALUES ('group',8,NULL,'member',100,1,2,0,NULL,9999999999999)")
        } }
        val bytes = Files.readAllBytes(path)
        assertFails { GroupPetRepository(path, manager).list("group") }
        assertContentEquals(bytes, Files.readAllBytes(path))
        DriverManager.getConnection(url).use { c -> c.createStatement().use {
            it.executeQuery("SELECT to_regclass('$schema.group_pet'), to_regclass('$schema.group_pet_migration')").use { r ->
                assertTrue(r.next()); assertNull(r.getString(1)); assertNull(r.getString(2))
            }
        } }
        DriverManager.getConnection("jdbc:sqlite:$path").use { c -> c.createStatement().use {
            it.execute("UPDATE group_pet SET nickname='fixed' WHERE player_id=8")
        } }
        assertEquals(2, GroupPetRepository(path, manager).list("group").size)
    }
}
