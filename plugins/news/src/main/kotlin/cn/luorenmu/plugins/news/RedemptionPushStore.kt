package cn.luorenmu.plugins.news

import cn.luorenmu.common.database.*
import org.ktorm.database.Database
import org.ktorm.dsl.*
import org.ktorm.schema.*
import java.nio.file.Path

internal data class CodePush(val code: String, val phase: String, val group: String, val body: String, val expiresAt: Long)

/** Durable at-most-once delivery ledger. Its claim is committed before any external send. */
internal class RedemptionPushStore(private val path: Path) {
    private fun <T> db(block: (Database) -> T): T = sqliteConnection(path).use { connection ->
        connection.schemaScript(javaClass,"/sql/redemption-push.sql")
        block(connection.ktorm())
    }
    fun enqueue(code: String, phases: List<String>, groups: List<String>, body: String, expiresAt: Long) = db { outer ->
        outer.useConnection { connection -> connection.writeTransaction { database ->
            var inserted = false
            phases.forEach { phase ->
                if (database.insertIgnoring(Events, Events.code, Events.phase) { set(it.code,code); set(it.phase,phase) } == 1) inserted = true
            }
            if (inserted && "ENDING" in phases) database.update(Deliveries) {
                set(it.state,"SUPERSEDED")
                where { (it.code eq code) and (it.phase eq "NEW") and (it.state eq "PENDING") }
            }
            if (inserted) groups.distinct().forEach { group ->
                database.insertIgnoring(Deliveries, Deliveries.code, Deliveries.phase, Deliveries.groupId) {
                    set(it.code,code); set(it.phase,phases.last()); set(it.groupId,group); set(it.body,body); set(it.expiresAt,expiresAt)
                }
            }
        } }
    }
    fun hasEvent(code: String, phase: String): Boolean = db { database ->
        database.from(Events).select(Events.code).where { (Events.code eq code) and (Events.phase eq phase) }.map { it[Events.code] }.isNotEmpty()
    }
    fun pending(now: Long): List<CodePush> = db { database ->
        database.from(Deliveries).select().where { (Deliveries.state eq "PENDING") and (Deliveries.expiresAt greater now) }
            .orderBy(Deliveries.expiresAt.asc()).limit(100).map { row ->
                CodePush(row[Deliveries.code]!!,row[Deliveries.phase]!!,row[Deliveries.groupId]!!,row[Deliveries.body]!!,row[Deliveries.expiresAt]!!)
            }
    }
    fun claim(push: CodePush, now: Long): Boolean = db { database ->
        database.update(Deliveries) {
            set(it.state,"ATTEMPTED")
            where { key(push) and (it.state eq "PENDING") and (it.expiresAt greater now) }
        } == 1
    }
    fun finish(push: CodePush, success: Boolean) = db { database ->
        database.update(Deliveries) { set(it.state,if (success) "SENT" else "UNCERTAIN"); where { key(push) and (it.state eq "ATTEMPTED") } }
    }
    private fun key(push: CodePush) = (Deliveries.code eq push.code) and (Deliveries.phase eq push.phase) and (Deliveries.groupId eq push.group)
}
private object Events : Table<Nothing>("code_push_event") {
    val code = varchar("code").primaryKey()
    val phase = varchar("phase").primaryKey()
}
private object Deliveries : Table<Nothing>("code_push_delivery") {
    val code = varchar("code").primaryKey()
    val phase = varchar("phase").primaryKey()
    val groupId = varchar("group_id").primaryKey()
    val body = varchar("body")
    val expiresAt = long("expires_at")
    val state = varchar("state")
}
