package cn.luorenmu.plugins.qqmedia

import cn.luorenmu.common.database.*
import org.ktorm.database.Database
import org.ktorm.dsl.*
import org.ktorm.schema.*
import java.nio.file.Path

/** Shared permission state; event time is monotonic and a close event wins a timestamp tie. */
class QQGroupPushPermissions(private val path: Path) {
    private fun <T> db(block: (Database) -> T): T = sqliteConnection(path).use { connection ->
        connection.schemaScript(javaClass,"/sql/qq-group-permissions.sql")
        block(connection.ktorm())
    }
    fun pushGroups(): List<String> = db { database ->
        database.from(Permissions).select(Permissions.groupId).where { Permissions.allowed eq 1 }.map { it[Permissions.groupId]!! }
    }
    fun canPush(group: String): Boolean = db { database ->
        database.from(Permissions).select(Permissions.allowed).where { Permissions.groupId eq group }.map { it[Permissions.allowed] == 1 }.firstOrNull() ?: false
    }
    fun updatePermission(group: String, allowed: Boolean, eventTime: Long) = db { outer ->
        outer.useConnection { connection -> connection.writeTransaction { database ->
            val added = database.insertIgnoring(Permissions, Permissions.groupId) {
                set(it.groupId,group); set(it.allowed,if (allowed) 1 else 0); set(it.eventTime,eventTime)
            }
            if (added == 1) return@writeTransaction 1
            database.update(Permissions) {
                set(it.allowed,if (allowed) 1 else 0); set(it.eventTime,eventTime)
                where { (it.groupId eq group) and (if (allowed) it.eventTime less eventTime else it.eventTime lessEq eventTime) }
            }
        } }
    }
}
private object Permissions : Table<Nothing>("group_pet_permission") {
    val groupId = varchar("group_id").primaryKey()
    val allowed = int("allowed")
    val eventTime = long("event_time")
}
