package cn.luorenmu.plugins.qqmedia

import cn.luorenmu.common.database.*
import io.github.oshai.kotlinlogging.KotlinLogging
import org.ktorm.database.Database
import org.ktorm.dsl.*
import org.ktorm.schema.*
import java.sql.Connection
import java.util.UUID

enum class QQPushKind(val value: String) { BILI_LIVE("bili-live"), GROUP_PET("group-pet") }

/** Acknowledged proactive sends only. Counts outlive subscription cancellation and expiry. */
class QQPushStatistics(private val connection: ((Connection) -> Unit) -> Unit) {
    private val log = KotlinLogging.logger {}
    private val schema by lazy {
        connection { c -> c.writeTransaction { database ->
            if (c.metaData.databaseProductName == "PostgreSQL") database.advisoryLock(18651, "qq-push-statistics")
            c.schemaScript(javaClass, "/sql/qq-push-statistics.sql")
        } }
    }

    private fun <T> db(write: Boolean = false, block: (Database) -> T): T {
        schema
        var result: Result<T>? = null
        connection { c -> result = runCatching { if (write) c.writeTransaction(block) else block(c.ktorm()) } }
        return checkNotNull(result).getOrThrow()
    }

    /** Stable receipt IDs make persistence retries safe even if a prior COMMIT's result was lost. */
    fun record(deliveryId: String, kind: QQPushKind, group: String, subject: Long, sentAt: Long): Boolean {
        require(deliveryId.isNotBlank() && group.isNotBlank() && subject > 0)
        return db(write = true) { database ->
            if (database.insertIgnoring(Receipts, Receipts.deliveryId) {
                set(it.deliveryId, deliveryId); set(it.kind, kind.value); set(it.groupId, group)
                set(it.subjectId, subject); set(it.sentAt, sentAt)
            } == 0) return@db false
            database.insertIgnoring(Statistics, Statistics.kind, Statistics.groupId, Statistics.subjectId) {
                set(it.kind, kind.value); set(it.groupId, group); set(it.subjectId, subject); set(it.pushCount, 0L)
            }
            check(database.update(Statistics) {
                set(it.pushCount, it.pushCount + 1L)
                where { (it.kind eq kind.value) and (it.groupId eq group) and (it.subjectId eq subject) }
            } == 1)
            true
        }
    }

    /** Retry bookkeeping, never the QQ send. A statistics outage must not cause duplicate messages. */
    fun recordSuccess(kind: QQPushKind, group: String, subject: Long, sentAt: Long): Boolean {
        val deliveryId = UUID.randomUUID().toString()
        var failure: Exception? = null
        repeat(3) {
            try {
                record(deliveryId, kind, group, subject, sentAt)
                return true
            } catch (error: Exception) { failure = error }
        }
        log.error { "主动推送统计写入失败：${kind.value}/${failure?.javaClass?.simpleName}，消息不会因统计失败而重发" }
        return false
    }

    fun count(kind: QQPushKind, subject: Long? = null, group: String? = null): Long = db { database ->
        database.from(Statistics).select(sum(Statistics.pushCount)).whereWithConditions { conditions ->
            conditions += Statistics.kind eq kind.value
            if (subject != null) conditions += Statistics.subjectId eq subject
            if (group != null) conditions += Statistics.groupId eq group
        }.map { it.getLong(1) }.single()
    }
}

private object Statistics : Table<Nothing>("qq_push_statistics") {
    val kind = varchar("push_kind").primaryKey()
    val groupId = varchar("group_id").primaryKey()
    val subjectId = long("subject_id").primaryKey()
    val pushCount = long("push_count")
}
private object Receipts : Table<Nothing>("qq_push_receipts") {
    val deliveryId = varchar("delivery_id").primaryKey()
    val kind = varchar("push_kind")
    val groupId = varchar("group_id")
    val subjectId = long("subject_id")
    val sentAt = long("sent_at")
}
