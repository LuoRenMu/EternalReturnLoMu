package cn.luorenmu.plugins.bililive

import cn.luorenmu.common.database.*
import cn.luorenmu.plugins.qqmedia.QQPushKind
import cn.luorenmu.plugins.qqmedia.QQPushStatistics
import org.ktorm.database.Database
import org.ktorm.dsl.*
import org.ktorm.schema.*
import java.sql.Connection
import java.util.UUID

internal data class BiliSubscription(val group: String, val room: Long, val status: Int, val session: String)
internal enum class LiveSubscriptionResult { ADDED, ALREADY_SUBSCRIBED, GROUP_LIMIT, USER_LIMIT, TOTAL_LIMIT, NOT_CLAIMED }
private data class LiveBinding(val group: String, val sender: String, val room: Long)

/** Uses the host pool and borrows its connection without closing it. */
internal class BiliLiveRepository(private val connection: ((Connection) -> Unit) -> Unit) {
    private val pushStatistics = QQPushStatistics(connection)
    fun recordPush(group: String, room: Long, sentAt: Long) = pushStatistics.recordSuccess(QQPushKind.BILI_LIVE, group, room, sentAt)
    fun pushCount(room: Long? = null, group: String? = null) = pushStatistics.count(QQPushKind.BILI_LIVE, room, group)
    // Independent DDL adds only side tables: legacy rows and their sent-session cursors are untouched.
    private val schema by lazy {
        connection { c -> c.writeTransaction { database ->
            if (c.metaData.databaseProductName == "PostgreSQL") database.advisoryLock(18641, "bili-live")
            c.schemaScript(javaClass, "/sql/bili-live.sql")
        } }
    }
    private fun <T> db(write: Boolean = false, block: (Database) -> T): T {
        schema
        var result: Result<T>? = null
        connection { c ->
            result = runCatching {
                if (write) c.writeTransaction { database ->
                    // Serialize group/user checks across plugin instances, including the shared-room global cap.
                    if (c.metaData.databaseProductName == "PostgreSQL") database.advisoryLock(18641, "bili-live")
                    block(database)
                } else block(c.ktorm())
            }
        }
        return checkNotNull(result).getOrThrow()
    }
    fun claim(group: String, sender: String): Boolean {
        if (group !in LIVE_CLAIM_GROUPS || !sender.matches(Regex("[A-Fa-f0-9]{32}"))) return false
        return db(write = true) { database ->
            database.insertIgnoring(Claims, Claims.senderId) { set(it.senderId, sender) }
            true // Repeated claims are idempotent, including after a restart.
        }
    }
    fun hasClaim(sender: String): Boolean = db { hasClaim(it, sender) }
    private fun hasClaim(database: Database, sender: String): Boolean = database.from(Claims)
        .select(Claims.senderId).where { Claims.senderId eq sender }.map { it[Claims.senderId] }.isNotEmpty()

    /** Raw legacy data is retained for listing/cancellation, but cannot authorize background sends. */
    fun rows(): List<BiliSubscription> = db { database ->
        database.from(Live).select().orderBy(Live.roomId.asc(),Live.groupId.asc()).map(::subscription)
    }
    fun activeRows(): List<BiliSubscription> = db { database ->
        database.from(Live)
            .innerJoin(Bindings, (Bindings.groupId eq Live.groupId) and (Bindings.roomId eq Live.roomId))
            .innerJoin(Claims, Claims.senderId eq Bindings.senderId)
            .select(Live.columns).orderBy(Live.roomId.asc(), Live.groupId.asc()).map(::subscription)
    }
    fun add(group: String, sender: String, room: BiliRoom): LiveSubscriptionResult = db(write = true) { database ->
        if (!hasClaim(database, sender)) return@db LiveSubscriptionResult.NOT_CLAIMED
        val bindings = database.from(Bindings).select().map { LiveBinding(it[Bindings.groupId]!!, it[Bindings.senderId]!!, it[Bindings.roomId]!!) }
        val current = bindings.singleOrNull { it.group == group }
        if (current != null && current.room != room.id) return@db LiveSubscriptionResult.GROUP_LIMIT
        // One user may use the SAME room in multiple owned groups, but never a second room.
        if (bindings.any { it.sender == sender && it.room != room.id }) return@db LiveSubscriptionResult.USER_LIMIT
        if (current != null) return@db LiveSubscriptionResult.ALREADY_SUBSCRIBED
        if (bindings.map { it.room }.distinct().size >= 50 && bindings.none { it.room == room.id })
            return@db LiveSubscriptionResult.TOTAL_LIMIT
        database.insertIgnoring(Live, Live.groupId, Live.roomId) {
            set(it.groupId,group); set(it.roomId,room.id); set(it.status,room.status); set(it.session,room.session)
        }
        database.insert(Bindings) { set(it.groupId,group); set(it.roomId,room.id); set(it.senderId,sender) }
        LiveSubscriptionResult.ADDED
    }
    fun remove(group: String, room: Long): Boolean = db(write = true) { database ->
        database.delete(Bindings) { (it.groupId eq group) and (it.roomId eq room) }
        database.delete(Live) { (it.groupId eq group) and (it.roomId eq room) } == 1
    }
    /** Commit the cursor and end-notice claim together, before any ambiguous external send. */
    fun advance(old: BiliSubscription, room: BiliRoom): Boolean = db(write = true) { database ->
        val active = database.from(Bindings).innerJoin(Claims, Claims.senderId eq Bindings.senderId)
            .select(Bindings.roomId).where { (Bindings.groupId eq old.group) and (Bindings.roomId eq old.room) }
            .map { it[Bindings.roomId] }.isNotEmpty()
        if (!active) return@db false
        val previousSession = old.session.removePrefix(INITIAL_LIVE_PREFIX)
        val session = if (room.status != 1 || (old.status == 1 && room.session.isBlank())) previousSession else room.session
        val updated = database.update(Live) {
            set(it.status,room.status); set(it.session,session)
            where { (it.groupId eq old.group) and (it.roomId eq old.room) and (it.status eq old.status) and (it.session eq old.session) }
        } == 1
        if (!updated) return@db false
        if (old.status != 1 || room.status == 1) return@db true
        // Known sessions suppress duplicate end notices after a 1 -> 0/2 -> 1 status flap, even after restart.
        // Without a usable upstream session, the persisted status CAS still claims each observed transition once.
        val endedSession = previousSession.takeUnless { it.isBlank() || it.startsWith("0000-00-00") }
            ?: "@unknown-ended:${UUID.randomUUID()}"
        database.insertIgnoring(Ended, Ended.groupId, Ended.roomId, Ended.session) {
            set(it.groupId, old.group); set(it.roomId, old.room); set(it.session, endedSession)
        } == 1
    }
    private fun subscription(row: QueryRowSet) = BiliSubscription(row[Live.groupId]!!, row[Live.roomId]!!, row[Live.status]!!, row[Live.session]!!)
}
private object Live : Table<Nothing>("test_bili_live") {
    val groupId = varchar("group_id").primaryKey()
    val roomId = long("room_id").primaryKey()
    val status = int("live_status")
    val session = varchar("live_session")
}
private object Claims : Table<Nothing>("bili_live_claims") {
    val senderId = varchar("sender_id").primaryKey()
}
private object Bindings : Table<Nothing>("bili_live_bindings") {
    val groupId = varchar("group_id").primaryKey()
    val roomId = long("room_id")
    val senderId = varchar("sender_id")
}
private object Ended : Table<Nothing>("bili_live_ended") {
    val groupId = varchar("group_id").primaryKey()
    val roomId = long("room_id").primaryKey()
    val session = varchar("live_session").primaryKey()
}
