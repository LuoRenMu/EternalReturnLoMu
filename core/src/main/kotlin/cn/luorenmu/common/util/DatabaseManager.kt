package cn.luorenmu.common.util

import cn.luorenmu.ConfigFile
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.oshai.kotlinlogging.KotlinLogging
import org.ktorm.database.Database

/**
 * PostgreSQL + Ktorm 数据库管理器，替代原有的 MongoDBManager。
 *
 * 使用 HikariCP 连接池管理 PostgreSQL 连接，通过 Ktorm 的 [Database] 暴露操作入口。
 *
 * @author LoMu
 * Date 2026/6/2
 */
open class DatabaseManager {
    private val logger = KotlinLogging.logger {}

    private val dataSource by lazy {
        if (ConfigFile.config.postgres.enabled) {
            createDataSource()
        } else {
            null
        }
    }

    open val database: Database? by lazy {
        dataSource?.let { Database.connect(it) }
    }

    open fun isEnabled(): Boolean = ConfigFile.config.postgres.enabled

    private fun createDataSource(): HikariDataSource {
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
        return HikariDataSource(config)
    }

    fun close() {
        dataSource?.close()
        logger.info { "PostgreSQL 连接池已关闭" }
    }
}
