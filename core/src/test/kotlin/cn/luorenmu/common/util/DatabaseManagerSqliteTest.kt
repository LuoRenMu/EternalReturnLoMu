package cn.luorenmu.common.util

import cn.luorenmu.service.AdminDatabaseService
import cn.luorenmu.service.AdminRowUpdate
import cn.luorenmu.repository.ExceptionRepository
import java.nio.file.Files
import java.sql.SQLException
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DatabaseManagerSqliteTest {
    @Test
    fun sqlitePersistsExceptionLogs() {
        val directory = Files.createTempDirectory("lomu-exception-log-test")
        val manager = DatabaseManager(DatabaseBackend.SQLITE, directory.resolve("exceptions.db"))

        try {
            manager.initialize()
            val repository = ExceptionRepository(manager)
            repository.record(
                IllegalStateException("render failed"),
                source = "command.unexpected",
                context = "command=/search tester",
            )

            val page = AdminDatabaseService(manager).page("exception_logs", limit = 10, offset = 0)
            assertEquals(1, page.total)
            assertEquals("command.unexpected", page.rows.single()["source"])
            assertEquals("render failed", page.rows.single()["message"])
            assertContains(page.rows.single()["stack_trace"].toString(), "IllegalStateException")
            val record = repository.list().single()
            assertEquals("command.unexpected", record.source)
            assertEquals("java.lang.IllegalStateException", record.exceptionType)
            assertContains(record.context, "/search tester")
            assertContains(record.stackTrace, "render failed")
        } finally {
            manager.close()
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun sqliteInitializesAndSupportsAdminRowUpdates() {
        val directory = Files.createTempDirectory("lomu-onebot-sqlite-test")
        val manager = DatabaseManager(DatabaseBackend.SQLITE, directory.resolve("onebot.db"))

        try {
            manager.initialize()
            manager.useConnection { connection ->
                connection.prepareStatement(
                    "INSERT INTO command_usage(command_name, nickname, group_id, sender_id, timestamp) VALUES (?, ?, ?, ?, ?)"
                ).use { statement ->
                    statement.setString(1, "/test")
                    statement.setString(2, "tester")
                    statement.setString(3, "group")
                    statement.setString(4, "sender")
                    statement.setString(5, "2026-08-11 12:00:00")
                    assertEquals(1, statement.executeUpdate())
                }
            }

            val admin = AdminDatabaseService(manager)
            assertContains(admin.tables().map { it.name }, "command_usage")
            val page = admin.page("command_usage", limit = 10, offset = 0)
            assertEquals(1, page.total)
            val id = page.rows.single().getValue("id")
            assertTrue(
                admin.update(
                    "command_usage",
                    AdminRowUpdate(
                        keys = mapOf("id" to id),
                        values = mapOf("command_name" to "/updated"),
                    )
                )
            )
            assertEquals("/updated", admin.page("command_usage", 10, 0).rows.single()["command_name"])
        } finally {
            manager.close()
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun fallsBackToSqliteWhenPostgresCannotConnect() {
        val directory = Files.createTempDirectory("lomu-postgres-fallback-test")
        val manager = DatabaseManager(
            backend = DatabaseBackend.POSTGRESQL,
            sqlitePath = directory.resolve("fallback.db"),
            postgresDataSourceFactory = { throw SQLException("PostgreSQL unavailable") },
        )

        try {
            manager.initialize()

            assertEquals(DatabaseBackend.SQLITE, manager.backend)
            assertEquals("SQLite", manager.displayName())
            manager.useConnection { connection ->
                connection.createStatement().use { statement ->
                    assertTrue(statement.executeQuery("SELECT 1").next())
                }
            }
            assertContains(AdminDatabaseService(manager).tables().map { it.name }, "command_usage")
        } finally {
            manager.close()
            directory.toFile().deleteRecursively()
        }
    }
}
