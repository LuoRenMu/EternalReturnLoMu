package cn.luorenmu.common.util

import cn.luorenmu.ConfigFile
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.oshai.kotlinlogging.KotlinLogging
import org.ktorm.database.Database
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.sql.Connection

/**
 * Ktorm 数据库管理器。OneBot 使用本地 SQLite，其他适配器按配置使用 PostgreSQL。
 *
 * 使用 HikariCP 管理 JDBC 连接，通过 Ktorm 的 [Database] 暴露统一操作入口。
 *
 * @author LoMu
 * Date 2026/6/2
 */
enum class DatabaseBackend {
    POSTGRESQL,
    SQLITE,
}

open class DatabaseManager(
    backend: DatabaseBackend = DatabaseBackend.POSTGRESQL,
    private val sqlitePath: Path = PathUtils.pathResolve(paths = arrayOf("data", "lomu.db")),
    private val postgresDataSourceFactory: (() -> HikariDataSource)? = null,
) {
    private val logger = KotlinLogging.logger {}

    @Volatile
    var backend: DatabaseBackend = backend
        private set

    private val dataSourceDelegate = lazy {
        if (isEnabled()) {
            createDataSourceWithFallback()
        } else {
            null
        }
    }
    private val dataSource: HikariDataSource?
        get() = dataSourceDelegate.value

    open val database: Database? by lazy {
        dataSource?.let { Database.connect(it) }
    }

    open fun isEnabled(): Boolean = backend == DatabaseBackend.SQLITE || ConfigFile.config.postgres.enabled

    fun schema(): String? = if (backend == DatabaseBackend.POSTGRESQL) ConfigFile.config.postgres.schema else null

    fun displayName(): String = when (backend) {
        DatabaseBackend.POSTGRESQL -> "PostgreSQL"
        DatabaseBackend.SQLITE -> "SQLite"
    }

    open fun initialize() {
        if (isEnabled()) {
            database
        }
    }

    fun <T> useConnection(block: (Connection) -> T): T {
        check(isEnabled()) { "${displayName()} 未启用" }
        return checkNotNull(database) { "${displayName()} 未连接" }.useConnection(block)
    }

    private fun createDataSourceWithFallback(): HikariDataSource {
        return when (backend) {
            DatabaseBackend.POSTGRESQL -> try {
                postgresDataSourceFactory?.invoke() ?: createPostgresDataSource()
            } catch (error: Exception) {
                logger.warn(error) {
                    "PostgreSQL 连接失败，自动切换到 SQLite: ${error.message ?: error.javaClass.simpleName}"
                }
                backend = DatabaseBackend.SQLITE
                createSqliteDataSource()
            }
            DatabaseBackend.SQLITE -> createSqliteDataSource()
        }
    }

    private fun createPostgresDataSource(): HikariDataSource {
        val pg = ConfigFile.config.postgres
        val config = HikariConfig().apply {
            jdbcUrl = "jdbc:postgresql://${pg.host}:${pg.port}/${pg.database}?currentSchema=${pg.schema}"
            username = pg.user
            password = pg.password
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = 10
            minimumIdle = 2
            idleTimeout = 30000
            connectionTimeout = 10000
            maxLifetime = 1800000
            // 连接验证
            connectionTestQuery = "SELECT 1"
            validationTimeout = 5000
        }
        logger.info { "PostgreSQL 连接池已初始化: ${pg.host}:${pg.port}/${pg.database}?currentSchema=${pg.schema}" }
        return createInitializedDataSource(config) { dataSource ->
            executeInitScript(dataSource, POSTGRES_INIT_SQL_RESOURCE)
        }
    }

    private fun createSqliteDataSource(): HikariDataSource {
        sqlitePath.parent?.toFile()?.mkdirs()
        val config = HikariConfig().apply {
            jdbcUrl = "jdbc:sqlite:${sqlitePath.toAbsolutePath().normalize()}"
            driverClassName = "org.sqlite.JDBC"
            maximumPoolSize = 1
            minimumIdle = 1
            connectionTimeout = 10000
            connectionTestQuery = "SELECT 1"
            poolName = "onebot-sqlite"
        }
        logger.info { "SQLite 数据库初始化: ${sqlitePath.toAbsolutePath().normalize()}" }
        return createInitializedDataSource(config) { dataSource ->
            dataSource.connection.use { connection ->
                connection.createStatement().use { statement ->
                    statement.execute("PRAGMA foreign_keys = ON")
                    statement.execute("PRAGMA busy_timeout = 5000")
                    statement.execute("PRAGMA journal_mode = WAL")
                }
            }
            executeInitScript(dataSource, SQLITE_INIT_SQL_RESOURCE)
        }
    }

    private inline fun createInitializedDataSource(
        config: HikariConfig,
        initialize: (HikariDataSource) -> Unit,
    ): HikariDataSource {
        val dataSource = HikariDataSource(config)
        return try {
            initialize(dataSource)
            dataSource
        } catch (error: Throwable) {
            dataSource.close()
            throw error
        }
    }

    private fun executeInitScript(dataSource: HikariDataSource, resource: String) {
        val sql = loadInitSql(resource)
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                sql.split(';')
                    .map(String::trim)
                    .filter(String::isNotEmpty)
                    .forEach(statement::execute)
            }
        }
        logger.info { "${displayName()} 初始化脚本已执行: $resource" }
    }

    private fun loadInitSql(resource: String): String {
        val classLoader = Thread.currentThread().contextClassLoader ?: this::class.java.classLoader
        val stream = classLoader.getResourceAsStream(resource)
            ?: error("未找到 ${displayName()} 初始化脚本: $resource")
        return stream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
    }

    private companion object {
        const val POSTGRES_INIT_SQL_RESOURCE = "sql/init_postgresql.sql"
        const val SQLITE_INIT_SQL_RESOURCE = "sql/init_sqlite.sql"
    }

    fun close() {
        if (dataSourceDelegate.isInitialized()) {
            dataSource?.close()
            logger.info { "${displayName()} 连接池已关闭" }
        }
    }
}
