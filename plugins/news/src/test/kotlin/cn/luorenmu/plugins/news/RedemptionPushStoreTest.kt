package cn.luorenmu.plugins.news

import cn.luorenmu.common.database.sqliteConnection
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.sql.SQLException
import kotlin.test.*

class RedemptionPushStoreTest {
    @TempDir lateinit var root: Path

    @Test fun `delivery failure rolls back phase ledger and preserves previous pending notification`() {
        val path = root.resolve("push.db")
        val store = RedemptionPushStore(path)
        store.enqueue("CODE", listOf("NEW"), listOf("group"), "new", 100)
        sqliteConnection(path).use { connection -> connection.createStatement().use {
            it.execute("CREATE TRIGGER reject_ending BEFORE INSERT ON code_push_delivery WHEN NEW.phase = 'ENDING' BEGIN SELECT RAISE(ABORT, 'fixture failure'); END")
        } }
        assertFailsWith<SQLException> { store.enqueue("CODE", listOf("ENDING"), listOf("group"), "ending", 100) }
        assertFalse(store.hasEvent("CODE", "ENDING"))
        assertEquals("NEW", store.pending(1).single().phase)
        sqliteConnection(path).use { connection -> connection.createStatement().use { it.execute("DROP TRIGGER reject_ending") } }
        store.enqueue("CODE", listOf("ENDING"), listOf("group"), "ending", 100)
        assertTrue(store.hasEvent("CODE", "ENDING"))
        assertEquals("ENDING", store.pending(1).single().phase)
    }
}
