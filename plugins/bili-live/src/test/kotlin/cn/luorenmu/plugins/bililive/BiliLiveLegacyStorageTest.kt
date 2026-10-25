package cn.luorenmu.plugins.bililive

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID
import kotlin.test.*

class BiliLiveLegacyStorageTest {
    @TempDir lateinit var root: Path

    private fun seed(connection: Connection) = connection.createStatement().use {
        it.execute("CREATE TABLE test_bili_live(group_id TEXT NOT NULL, room_id BIGINT NOT NULL, live_status INTEGER NOT NULL, live_session TEXT NOT NULL, PRIMARY KEY(group_id,room_id))")
        it.execute("INSERT INTO test_bili_live VALUES('legacy-group',5000000000,1,'2026-10-23 08:00:00')")
    }

    private fun verify(connection: ((Connection) -> Unit) -> Unit) {
        val repository = BiliLiveRepository(connection)
        val original = repository.rows().single()
        assertEquals(BiliSubscription("legacy-group", 5_000_000_000L, 1, "2026-10-23 08:00:00"), original)
        val current = BiliRoom(original.room, "new title", 1, "2026-10-23 09:00:00")
        assertTrue(repository.activeRows().isEmpty(), "legacy rows have no verified claimant and must remain paused")
        assertFalse(repository.advance(original, current), "loading the new plugin cannot authorize or consume an unclaimed legacy cursor")
        val sender = "F".repeat(32)
        assertEquals(LiveSubscriptionResult.NOT_CLAIMED, repository.add(original.group, sender, current))
        assertTrue(repository.claim(LIVE_CLAIM_GROUP, sender))
        assertEquals(LiveSubscriptionResult.ADDED, repository.add(original.group, sender, current))
        assertEquals(LiveSubscriptionResult.ALREADY_SUBSCRIBED, repository.add(original.group, sender, current))
        assertEquals(original, repository.rows().single())
        assertEquals(listOf(original), repository.activeRows())
        assertTrue(repository.advance(original, current))
        assertFalse(repository.advance(original, current))
        assertEquals(current.session, BiliLiveRepository(connection).rows().single().session)
        assertTrue(repository.remove(original.group, original.room))
        assertFalse(repository.advance(original, current), "a stale worker cannot recreate a cancelled subscription")
    }

    @Test fun `sqlite existing test subscription is taken over without resetting any cursor`() {
        val url = "jdbc:sqlite:${root.resolve("legacy.db")}"
        DriverManager.getConnection(url).use(::seed)
        verify { block -> DriverManager.getConnection(url).use { connection -> block(connection); assertFalse(connection.isClosed) } }
    }

    @Test fun `postgres existing test subscription is taken over in the hosts current schema`() {
        val url = System.getenv("GROUP_PET_PG_TEST_URL")
        assumeTrue(!url.isNullOrBlank(), "Requires isolated PostgreSQL fixture")
        val schema = "live_legacy_" + UUID.randomUUID().toString().replace("-", "")
        DriverManager.getConnection(url).use { c -> c.createStatement().use { it.execute("CREATE SCHEMA $schema") } }
        try {
            DriverManager.getConnection(url).use { c -> c.schema = schema; seed(c) }
            verify { block -> DriverManager.getConnection(url).use { c -> c.schema = schema; block(c); assertFalse(c.isClosed) } }
        } finally {
            check(schema.matches(Regex("live_legacy_[a-f0-9]{32}")))
            DriverManager.getConnection(url).use { c -> c.createStatement().use { it.execute("DROP SCHEMA $schema CASCADE") } }
        }
    }
}
