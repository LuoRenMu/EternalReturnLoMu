package cn.luorenmu.repository

import cn.luorenmu.common.util.DatabaseManager
import cn.luorenmu.repository.table.ExceptionLogs
import cn.luorenmu.repository.entity.ExceptionLogRecord
import io.github.oshai.kotlinlogging.KotlinLogging
import org.ktorm.dsl.desc
import org.ktorm.dsl.from
import org.ktorm.dsl.insert
import org.ktorm.dsl.limit
import org.ktorm.dsl.map
import org.ktorm.dsl.orderBy
import org.ktorm.dsl.select
import java.time.LocalDateTime

/** Persists unexpected runtime failures without ever masking the original exception. */
open class ExceptionRepository(private val databaseManager: DatabaseManager) {
    private val logger = KotlinLogging.logger {}

    open fun record(error: Throwable, source: String, context: String = "") {
        if (!databaseManager.isEnabled()) return
        val database = databaseManager.database ?: return

        runCatching {
            database.insert(ExceptionLogs) {
                set(it.source, source.take(MAX_SOURCE_LENGTH))
                set(it.exceptionType, (error::class.qualifiedName ?: error.javaClass.name).take(MAX_TYPE_LENGTH))
                set(it.message, error.message.orEmpty())
                set(it.context, context)
                set(it.stackTrace, error.stackTraceToString())
                set(it.occurredAt, LocalDateTime.now())
            }
        }.onFailure { persistenceError ->
            logger.error(persistenceError) { "异常记录写入数据库失败: ${persistenceError.message}" }
        }
    }

    open fun list(limit: Int = 100): List<ExceptionLogRecord> {
        require(limit in 1..500) { "limit 必须在 1 到 500 之间" }
        if (!databaseManager.isEnabled()) return emptyList()
        val database = databaseManager.database ?: return emptyList()

        return runCatching {
            database.from(ExceptionLogs)
                .select()
                .orderBy(ExceptionLogs.occurredAt.desc(), ExceptionLogs.id.desc())
                .limit(limit)
                .map { row ->
                    ExceptionLogRecord(
                        id = row[ExceptionLogs.id] ?: 0L,
                        source = row[ExceptionLogs.source].orEmpty(),
                        exceptionType = row[ExceptionLogs.exceptionType].orEmpty(),
                        message = row[ExceptionLogs.message].orEmpty(),
                        context = row[ExceptionLogs.context].orEmpty(),
                        stackTrace = row[ExceptionLogs.stackTrace].orEmpty(),
                        occurredAt = (row[ExceptionLogs.occurredAt] ?: LocalDateTime.now()).toString(),
                    )
                }
        }.onFailure { error ->
            logger.error(error) { "读取异常日志失败: ${error.message}" }
        }.getOrDefault(emptyList())
    }

    private companion object {
        const val MAX_SOURCE_LENGTH = 100
        const val MAX_TYPE_LENGTH = 255
    }
}
