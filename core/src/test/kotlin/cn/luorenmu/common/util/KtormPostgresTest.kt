package cn.luorenmu.common.util

import cn.luorenmu.ConfigFile
import cn.luorenmu.common.database.schemaScript
import cn.luorenmu.repository.NewsRepository
import cn.luorenmu.repository.entity.EternalReturnNewsRecord
import cn.luorenmu.service.AdminDatabaseService
import cn.luorenmu.service.AdminRowUpdate
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.sql.DriverManager
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.test.*

class KtormPostgresTest {
    @TempDir lateinit var root: Path

    private fun fixture(block: (DatabaseManager) -> Unit) {
        val url = System.getenv("GROUP_PET_PG_TEST_URL")
        assumeTrue(!url.isNullOrBlank(), "Requires isolated PostgreSQL fixture")
        val schema = "dsl_test_" + UUID.randomUUID().toString().replace("-", "")
        DriverManager.getConnection(url).use { connection ->
            connection.createStatement().use { it.execute("CREATE SCHEMA $schema") }
        }
        val previousSchema = ConfigFile.config.postgres.schema
        val manager = DatabaseManager(DatabaseBackend.POSTGRESQL, root.resolve("unused.db"), {
            HikariDataSource(HikariConfig().apply {
                jdbcUrl = url + (if (url!!.contains('?')) "&" else "?") + "currentSchema=$schema"
                // A single connection also detects accidental nested acquisition in admin queries.
                maximumPoolSize = 1
                minimumIdle = 0
            })
        })
        try {
            ConfigFile.config.postgres.schema = schema
            manager.initialize()
            assertEquals(DatabaseBackend.POSTGRESQL, manager.backend)
            manager.useConnection { it.schemaScript(DatabaseManager::class.java, "/sql/init_postgresql.sql") }
            block(manager)
        } finally {
            ConfigFile.config.postgres.schema = previousSchema
            manager.close()
            check(schema.matches(Regex("dsl_test_[a-f0-9]{32}")))
            DriverManager.getConnection(url).use { connection ->
                connection.createStatement().use { it.execute("DROP SCHEMA $schema CASCADE") }
            }
        }
    }

    @Test fun `postgres conflict ignore preserves first article and nullable timestamps`() = fixture { manager ->
        val repository = NewsRepository(manager)
        val record = EternalReturnNewsRecord(articleId = 3867, title = "韩文 백수", code = "ORIGINAL",
            isRedemptionCode = true, processedAt = LocalDateTime.of(2026, 10, 23, 12, 0))
        val executor = Executors.newFixedThreadPool(4)
        try {
            assertEquals(1, executor.invokeAll(List(12) { Callable { repository.insert(record) } }).count { it.get() })
        } finally { executor.shutdownNow() }
        assertFalse(repository.insert(record.copy(code = "REPLACED")))
        val stored = repository.findLatestRedemptionCodes().single()
        assertEquals(record.copy(id = stored.id), stored)
        assertTrue(repository.isArticleExists(3867))
    }

    @Test fun `postgres admin DSL supports literal filters numeric sorting and typed nullable updates`() = fixture { manager ->
        manager.useConnection { connection -> connection.createStatement().use {
            it.execute("CREATE TABLE dsl_admin(id BIGINT PRIMARY KEY, label TEXT, active BOOLEAN, amount NUMERIC, at TIMESTAMP, optional TEXT)")
            it.execute("INSERT INTO dsl_admin VALUES (5000000000, 'Alice%_!', true, 1.25, '2026-10-23 12:00:00', 'old'), (2, 'alice', false, 2, NULL, NULL)")
        } }
        val admin = AdminDatabaseService(manager)
        assertEquals(2, admin.tables().single { it.name == "dsl_admin" }.rowCount)
        assertEquals("5000000000", admin.page("dsl_admin", 1, 0, sortColumn = "id", sortDirection = "desc").rows.single()["id"])
        assertEquals(2, admin.page("dsl_admin", 1, 0, "label", "ALICE").total)
        assertEquals(1, admin.page("dsl_admin", 10, 0, "label", "%_!").total)
        assertEquals(0, admin.page("dsl_admin", 10, 0, "label", "' OR 1=1 --").total)
        assertTrue(admin.update("dsl_admin", AdminRowUpdate(mapOf("id" to "5000000000"),
            mapOf("active" to "false", "amount" to "123.45", "at" to "2026-10-24T13:00:00", "optional" to null))))
        val row = admin.page("dsl_admin", 10, 0, "label", "%_!").rows.single()
        assertEquals("false", row["active"])
        assertEquals("123.45", row["amount"])
        assertEquals("2026-10-24 13:00:00.0", row["at"])
        assertNull(row["optional"])
        assertFailsWith<IllegalArgumentException> { admin.page("dsl_admin", 10, 0, sortColumn = "label") }
    }
}
