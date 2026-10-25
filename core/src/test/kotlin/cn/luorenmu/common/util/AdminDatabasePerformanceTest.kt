package cn.luorenmu.common.util

import cn.luorenmu.service.AdminDatabaseService
import org.ktorm.database.Database
import org.ktorm.support.sqlite.SQLiteDialect
import org.sqlite.SQLiteDataSource
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.nio.file.Files
import java.sql.Connection
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals

class AdminDatabasePerformanceTest {
    @Test
    fun `paging reuses metadata but returns fresh rows and exact totals`() {
        val directory = Files.createTempDirectory("admin-db-performance")
        val source = SQLiteDataSource().apply { url = "jdbc:sqlite:${directory.resolve("test.db")}" }
        var metadataReads = 0
        var counts = 0
        val measured = object : DataSource by source {
            override fun getConnection(): Connection {
                val delegate = source.connection
                return Proxy.newProxyInstance(Connection::class.java.classLoader, arrayOf(Connection::class.java)) { _, method, args ->
                    if (method.name == "getMetaData") metadataReads++
                    if (method.name == "prepareStatement" && args?.firstOrNull().toString().contains("count(*)", ignoreCase = true)) counts++
                    // COUNT can also be issued by a plain Statement.
                    val result = try { method.invoke(delegate, *(args ?: emptyArray())) }
                    catch (error: InvocationTargetException) { throw error.targetException }
                    if (method.name == "createStatement") {
                        Proxy.newProxyInstance(java.sql.Statement::class.java.classLoader, arrayOf(java.sql.Statement::class.java)) { _, operation, parameters ->
                            if (operation.name == "executeQuery" && parameters?.firstOrNull().toString().contains("count(*)", ignoreCase = true)) counts++
                            try { operation.invoke(result, *(parameters ?: emptyArray())) }
                            catch (error: InvocationTargetException) { throw error.targetException }
                        }
                    } else result
                } as Connection
            }
        }
        val manager = object : DatabaseManager(DatabaseBackend.SQLITE) {
            override val database = Database.connect(measured, dialect = SQLiteDialect())
        }
        try {
            source.connection.use { connection ->
                connection.createStatement().use {
                    it.execute("CREATE TABLE sample(id INTEGER PRIMARY KEY, name TEXT)")
                    it.execute("INSERT INTO sample VALUES (1, 'Alice'), (2, 'Bob'), (3, 'Alice')")
                }
            }
            metadataReads = 0 // Exclude Ktorm's one-time dialect detection.
            val admin = AdminDatabaseService(manager)
            assertEquals(3, admin.page("sample", 10, 0).total)
            source.connection.use { it.createStatement().use { statement -> statement.execute("INSERT INTO sample VALUES (4, 'Carol')") } }
            assertEquals(4, admin.page("sample", 10, 0).total)
            assertEquals(3, metadataReads, "warm pagination must not reload table, keys and columns")
            assertEquals(0, counts, "a complete first page already provides the exact total")
            assertEquals(4, admin.page("sample", 2, 0).total)
            assertEquals(2, admin.page("sample", 10, 0, "name", "alice").total)
            assertEquals(1, counts, "full pages still need an exact count; complete filtered pages do not")
            assertEquals(4, admin.page("sample", 10, 100).total, "empty out-of-range pages must count")
            source.connection.use { it.createStatement().use { statement ->
                statement.execute("ALTER TABLE sample ADD COLUMN extra TEXT")
                statement.execute("CREATE TABLE second(id INTEGER PRIMARY KEY)")
            } }
            counts = 0
            assertEquals(listOf("sample", "second"), admin.tables().map { it.name })
            assertEquals(1, counts, "table totals must use one SQL round trip")
            assertEquals("extra", admin.page("sample", 10, 0).columns.last().name,
                "refreshing tables must invalidate cached schema")
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
