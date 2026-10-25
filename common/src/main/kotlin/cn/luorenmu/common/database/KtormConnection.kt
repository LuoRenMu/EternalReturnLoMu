package cn.luorenmu.common.database

import org.ktorm.database.Database
import org.ktorm.dsl.*
import org.ktorm.expression.*
import org.ktorm.logging.NoOpLogger
import org.ktorm.schema.*
import org.ktorm.support.postgresql.PostgreSqlDialect
import org.ktorm.support.postgresql.insertOrUpdate as pgInsertOrUpdate
import org.ktorm.support.sqlite.SQLiteDialect
import org.ktorm.support.sqlite.insertOrUpdate as sqliteInsertOrUpdate
import org.sqlite.SQLiteConfig
import org.sqlite.SQLiteConnection
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.sql.Connection

/** Ktorm may borrow the current JDBC transaction but must never close its owner's connection. */
fun Connection.ktorm(): Database {
    val owner = this
    val borrowed = Proxy.newProxyInstance(Connection::class.java.classLoader, arrayOf(Connection::class.java)) { _, method, args ->
        if (method.name == "close") null else try {
            method.invoke(owner, *(args ?: emptyArray()))
        } catch (e: InvocationTargetException) { throw e.targetException }
    } as Connection
    val dialect = when (metaData.databaseProductName) {
        "PostgreSQL" -> PostgreSqlDialect()
        "SQLite" -> SQLiteDialect()
        else -> error("Unsupported database: ${metaData.databaseProductName}")
    }
    // This is a short-lived facade, not a newly opened connection; avoid a "Connected" log per query.
    return Database.connect(connector = { borrowed }, dialect = dialect, logger = NoOpLogger)
}

/** Same dialect-neutral DSL API, with each formatter's own conflict expression. No read-before-insert race. */
fun <T : BaseTable<*>> Database.insertIgnoring(table: T, vararg keys: Column<*>, block: AssignmentsBuilder.(T) -> Unit): Int =
    if (dialect is SQLiteDialect) sqliteInsertOrUpdate(table) {
        block(it)
        onConflict(*keys) { doNothing() }
    } else pgInsertOrUpdate(table) {
        block(it)
        onConflict(*keys) { doNothing() }
    }

/** Preserve SQLite's write-first locking; transaction control is JDBC, not handwritten BEGIN/COMMIT SQL. */
fun <T> Connection.writeTransaction(block: (Database) -> T): T {
    check(autoCommit) { "A write transaction must own its connection" }
    val sqlite = if (metaData.databaseProductName == "SQLite") unwrap(SQLiteConnection::class.java) else null
    val mode = sqlite?.connectionConfig?.transactionMode
    if (sqlite != null) sqlite.connectionConfig.transactionMode = SQLiteConfig.TransactionMode.IMMEDIATE
    try {
        autoCommit = false
        try {
            val result = block(ktorm())
            commit()
            return result
        } catch (e: Throwable) {
            try { rollback() } catch (rollback: Throwable) { e.addSuppressed(rollback) }
            throw e
        } finally { autoCommit = true }
    } finally { if (sqlite != null && mode != null) sqlite.connectionConfig.transactionMode = mode }
}

/** PostgreSQL transaction lock expressed through Ktorm's typed SQL-function AST. */
fun Database.advisoryLock(namespace: Int, key: String) {
    val hash = FunctionExpression("hashtext", listOf(ArgumentExpression(key, VarcharSqlType)), IntSqlType)
    val lock = FunctionExpression("pg_advisory_xact_lock", listOf(ArgumentExpression(namespace, IntSqlType), hash), VarcharSqlType)
    // Ktorm 3.6 requires a FROM source; this catalog predicate has exactly one row.
    from(PgLockAnchor).select(lock).where { PgLockAnchor.setting eq "server_version" }.map { it.getString(1) }.single()
}

/** Only schema/migration scripts may contain native SQL; repositories use DSL for data operations. */
fun Connection.schemaScript(anchor: Class<*>, resource: String) {
    val sql = checkNotNull(anchor.getResourceAsStream(resource)) { "Missing migration: $resource" }.bufferedReader().use { it.readText() }
    createStatement().use { statement ->
        sql.split(';').map(String::trim).filter(String::isNotEmpty).forEach(statement::execute)
    }
}

fun sqliteConnection(path: java.nio.file.Path): Connection {
    java.nio.file.Files.createDirectories(path.toAbsolutePath().parent)
    Class.forName("org.sqlite.JDBC")
    val properties = java.util.Properties().apply { setProperty("busy_timeout", "5000") }
    return java.sql.DriverManager.getConnection("jdbc:sqlite:${path.toAbsolutePath()}", properties)
}

private object PgLockAnchor : Table<Nothing>("pg_settings", schema = "pg_catalog") {
    val setting = varchar("name")
}
