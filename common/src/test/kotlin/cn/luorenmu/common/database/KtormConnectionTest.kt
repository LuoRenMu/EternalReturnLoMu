package cn.luorenmu.common.database

import org.junit.jupiter.api.io.TempDir
import org.ktorm.dsl.*
import org.ktorm.schema.*
import java.nio.file.Path
import kotlin.test.*

class KtormConnectionTest {
    @TempDir lateinit var root: Path

    private fun connection() = sqliteConnection(root.resolve("test.db")).apply {
        createStatement().use { it.execute("CREATE TABLE IF NOT EXISTS sample(id INTEGER PRIMARY KEY, value TEXT)") }
    }

    @Test fun `conflict ignore is atomic and does not overwrite nullable data`() = connection().use { connection ->
        val database = connection.ktorm()
        assertEquals(1, database.insertIgnoring(Sample, Sample.id) { set(it.id, 1); set(it.value, null as String?) })
        assertEquals(0, database.insertIgnoring(Sample, Sample.id) { set(it.id, 1); set(it.value, "replacement") })
        assertNull(database.from(Sample).select().map { it[Sample.value] }.single())
        assertFalse(connection.isClosed, "Ktorm must not close its owner's JDBC connection")
    }

    @Test fun `transaction rolls back and restores original connection mode`() = connection().use { connection ->
        val sqlite = connection.unwrap(org.sqlite.SQLiteConnection::class.java)
        val mode = sqlite.connectionConfig.transactionMode
        assertFailsWith<IllegalStateException> {
            connection.writeTransaction { database ->
                database.insert(Sample) { set(it.id, 1); set(it.value, "rollback") }
                error("fail after insert")
            }
        }
        assertTrue(connection.autoCommit)
        assertEquals(mode, sqlite.connectionConfig.transactionMode)
        assertEquals(0, connection.ktorm().from(Sample).select(count()).map { it.getInt(1) }.single())
        connection.writeTransaction { database -> database.insert(Sample) { set(it.id, 2) } }
        assertEquals(2, connection.ktorm().from(Sample).select().map { it[Sample.id] }.single())
    }

    @Test fun `borrowed transaction sees its own writes without committing early`() = connection().use { connection ->
        connection.writeTransaction { database ->
            database.insert(Sample) { set(it.id, 1) }
            assertEquals(1, database.from(Sample).select().map { it[Sample.id] }.single())
            connection().use { other ->
                assertEquals(0, other.ktorm().from(Sample).select(count()).map { it.getInt(1) }.single())
            }
            assertFalse(connection.autoCommit)
        }
        connection().use { other -> assertEquals(1, other.ktorm().from(Sample).select(count()).map { it.getInt(1) }.single()) }
    }
}

private object Sample : Table<Nothing>("sample") {
    val id = int("id").primaryKey()
    val value = varchar("value")
}
