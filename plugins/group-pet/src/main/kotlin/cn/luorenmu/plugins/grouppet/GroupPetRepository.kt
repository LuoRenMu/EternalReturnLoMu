package cn.luorenmu.plugins.grouppet

import cn.luorenmu.common.database.*
import cn.luorenmu.common.util.DatabaseManager
import cn.luorenmu.plugins.qqmedia.QQPushKind
import cn.luorenmu.plugins.qqmedia.QQPushStatistics
import org.ktorm.database.Database
import org.ktorm.dsl.*
import org.ktorm.schema.*
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection

/** Both backends use the same typed DML; migration and write locks retain their original transaction. */
internal class GroupPetRepository(private val path: Path, private val manager: DatabaseManager? = null) {
    private val pushStatistics = QQPushStatistics { action -> db { database -> database.useConnection(action) } }
    fun recordPush(group: String, playerId: Long, sentAt: Long) = pushStatistics.recordSuccess(QQPushKind.GROUP_PET, group, playerId, sentAt)
    fun pushCount(playerId: Long? = null, group: String? = null) = pushStatistics.count(QQPushKind.GROUP_PET, playerId, group)
    @Volatile private var initialized = false
    @Volatile private var sqliteInitialized = false

    private fun <T> db(block: (Database) -> T): T {
        if (manager == null) return sqliteDb(block)
        return manager.useConnection { connection ->
            if (connection.metaData.databaseProductName != "PostgreSQL") return@useConnection sqliteDb(block)
            if (!initialized) synchronized(this) {
                if (!initialized) {
                    initializePostgres(connection)
                    initialized = true
                }
            }
            block(connection.ktorm())
        }
    }

    private fun <T> sqliteDb(block: (Database) -> T): T = sqliteConnection(path).use { connection ->
        if (!sqliteInitialized) synchronized(this) {
            if (!sqliteInitialized) {
                connection.writeTransaction {
                    connection.schemaScript(javaClass, "/sql/group-pet-sqlite.sql")
                    if (!connection.hasPetColumn("expires_at")) connection.schemaScript(javaClass, "/sql/group-pet-add-expiry.sql")
                    if (!connection.hasPetColumn("inactivity_match_id")) connection.schemaScript(javaClass, "/sql/group-pet-add-inactivity.sql")
                    if (!connection.hasPetColumn("expiry_notice_next_at")) connection.schemaScript(javaClass, "/sql/group-pet-add-expiry-notice.sql")
                    connection.schemaScript(javaClass, "/sql/group-pet-expiry-index.sql")
                }
                sqliteInitialized = true
            }
        }
        block(connection.ktorm())
    }

    private fun initializePostgres(connection: Connection) = connection.writeTransaction { database ->
        database.advisoryLock(18631, connection.schema.orEmpty())
        connection.schemaScript(javaClass,"/sql/group-pet-postgresql.sql")
        if (!connection.hasPetColumn("inactivity_match_id")) connection.schemaScript(javaClass, "/sql/group-pet-add-inactivity.sql")
        if (!connection.hasPetColumn("expiry_notice_next_at")) connection.schemaScript(javaClass, "/sql/group-pet-add-expiry-notice.sql")
        connection.schemaScript(javaClass, "/sql/group-pet-expiry-index.sql")
        if (database.from(Migration).select(Migration.source).where { Migration.source eq "legacy-sqlite-v1" }.map { it[Migration.source] }.isEmpty()) {
            importLegacySubscriptions(database)
            database.insert(Migration) { set(it.source,"legacy-sqlite-v1"); set(it.completedAt,System.currentTimeMillis()) }
        }
    }

    private fun importLegacySubscriptions(target: Database) {
        if (!Files.exists(path)) return
        sqliteConnection(path).use { source ->
            val exists = source.metaData.getTables(null,null,"group_pet",arrayOf("TABLE")).use { it.next() }
            if (!exists) return
            val hasExpiry = source.hasPetColumn("expires_at")
            val hasInactivity = source.hasPetColumn("inactivity_match_id")
            val hasExpiryNotice = source.hasPetColumn("expiry_notice_next_at")
            val selected = listOf(Pets.groupId, Pets.playerId, Pets.nickname, Pets.requesterId, Pets.lastMatchId,
                Pets.lastMatchTime, Pets.nextCheckAt, Pets.failures, Pets.lastError) +
                (if (hasExpiry) listOf(Pets.expiresAt) else emptyList()) +
                (if (hasInactivity) listOf(Pets.inactivityMatchId) else emptyList()) +
                (if (hasExpiryNotice) listOf(Pets.expiryNoticeNextAt) else emptyList())
            source.ktorm().from(Pets).select(selected).forEach { row ->
                target.insertIgnoring(Pets, Pets.groupId, Pets.playerId) {
                    set(it.groupId,row[Pets.groupId]); set(it.playerId,row[Pets.playerId])
                    set(it.nickname,row[Pets.nickname]); set(it.requesterId,row[Pets.requesterId])
                    set(it.lastMatchId,row[Pets.lastMatchId]); set(it.lastMatchTime,row[Pets.lastMatchTime])
                    set(it.nextCheckAt,row[Pets.nextCheckAt]); set(it.failures,row[Pets.failures]); set(it.lastError,row[Pets.lastError])
                    set(it.expiresAt,if (hasExpiry) row[Pets.expiresAt] else 0L)
                    set(it.inactivityMatchId,if (hasInactivity) row[Pets.inactivityMatchId] else null)
                    set(it.expiryNoticeNextAt,if (hasExpiryNotice) row[Pets.expiryNoticeNextAt] ?: 0L else 0L)
                }
            }
        }
    }

    private fun Connection.hasPetColumn(name: String): Boolean = metaData.getColumns(
        null, if (metaData.databaseProductName == "PostgreSQL") schema else null, "group_pet", name,
    ).use { it.next() }

    private val permissions = cn.luorenmu.plugins.qqmedia.QQGroupPushPermissions(path)
    fun canPush(group: String) = permissions.canPush(group)
    fun updatePermission(group: String, allowed: Boolean, eventTime: Long) = permissions.updatePermission(group, allowed, eventTime)

    fun list(group: String): List<PetSubscription> = db { database ->
        database.from(Pets).select().where { Pets.groupId eq group }.orderBy(Pets.nickname.asc()).map(::subscription)
    }

    fun add(group: String, requester: String, snapshot: PetSnapshot, now: Long): Boolean = db { outer ->
        outer.useConnection { connection -> connection.writeTransaction { database ->
            if (connection.metaData.databaseProductName == "PostgreSQL") database.advisoryLock(18632, group)
            // Renewing this player cancels its old notice, not the pending notices of other expired players.
            database.delete(Pets) { (it.groupId eq group) and (it.playerId eq snapshot.playerId) and (it.expiresAt lessEq now) }
            val duplicate = database.from(Pets).select(Pets.playerId).where { (Pets.groupId eq group) and (Pets.playerId eq snapshot.playerId) }.map { it[Pets.playerId] }.isNotEmpty()
            if (duplicate) return@writeTransaction false
            val count = database.from(Pets).select(count()).where { (Pets.groupId eq group) and (Pets.expiresAt greater now) }.map { it.getInt(1) }.single()
            require(count < 3) { "每群最多订阅 3 位群宠，请先取消旧订阅" }
            database.insertIgnoring(Pets, Pets.groupId, Pets.playerId) {
                set(it.groupId,group); set(it.playerId,snapshot.playerId); set(it.nickname,snapshot.nickname); set(it.requesterId,requester)
                set(it.lastMatchId,snapshot.latest?.id); set(it.lastMatchTime,snapshot.latest?.startedAt)
                set(it.nextCheckAt,now + PetPollingPolicy.interval(snapshot.latest?.startedAt,now).toMillis())
                set(it.expiresAt,now + PetPollingPolicy.subscriptionDuration.toMillis())
            } == 1
        } }
    }

    fun discard(row: PetSubscription, owner: String): Boolean = db { database ->
        database.delete(Pets) { leased(row,owner) } == 1
    }
    fun remove(group: String, playerId: Long): Boolean = db { database ->
        database.delete(Pets) { (it.groupId eq group) and (it.playerId eq playerId) } == 1
    }
    fun due(now: Long): List<PetSubscription> = db { database ->
        database.from(Pets).select().where { (Pets.nextCheckAt lessEq now) and (Pets.leaseUntil lessEq now) and (Pets.expiresAt greater now) }
            .orderBy(Pets.nextCheckAt.asc()).limit(30).map(::subscription)
    }
    fun expiredDue(now: Long): List<PetSubscription> = db { database ->
        database.from(Pets).select().where { (Pets.expiresAt greater 0L) and (Pets.expiresAt lessEq now) and
            (Pets.expiryNoticeNextAt lessEq now) and (Pets.leaseUntil lessEq now) }
            .orderBy(Pets.expiresAt.asc()).limit(30).map(::subscription)
    }
    fun claimExpiry(row: PetSubscription, owner: String, now: Long): Boolean = db { database ->
        database.update(Pets) {
            set(it.leaseOwner,owner); set(it.leaseUntil,now+300_000)
            where { (it.groupId eq row.groupId) and (it.playerId eq row.playerId) and (it.expiresAt eq row.expiresAt) and
                (it.expiresAt greater 0L) and (it.expiresAt lessEq now) and (it.leaseUntil lessEq now) and
                (it.expiryNoticeNextAt eq row.expiryNoticeNextAt) and (it.expiryNoticeNextAt lessEq now) }
        } == 1
    }
    fun ownsExpiry(row: PetSubscription, owner: String, now: Long): Boolean = db { database ->
        database.from(Pets).select(Pets.playerId).where { leased(row,owner) and
            (Pets.expiresAt greater 0L) and (Pets.expiresAt lessEq now) and (Pets.leaseUntil greater now) }
            .map { it[Pets.playerId] }.isNotEmpty()
    }
    fun deferExpiry(row: PetSubscription, owner: String, nextAt: Long, failures: Int, error: String) = db { database ->
        database.update(Pets) {
            set(it.expiryNoticeNextAt,nextAt); set(it.failures,failures); set(it.lastError,error.take(100))
            set(it.leaseOwner,null as String?); set(it.leaseUntil,0L)
            where { leased(row,owner) }
        }
    }
    fun completeExpiry(row: PetSubscription, owner: String): Boolean = discard(row,owner)
    fun claim(row: PetSubscription, owner: String, now: Long): Boolean = db { database ->
        database.update(Pets) {
            set(it.leaseOwner,owner); set(it.leaseUntil,now+300_000)
            where { (it.groupId eq row.groupId) and (it.playerId eq row.playerId) and (it.leaseUntil lessEq now) and
                (it.nextCheckAt eq row.nextCheckAt) and (it.nextCheckAt lessEq now) and (it.expiresAt greater now) and
                (if (row.inactivityMatchId == null) it.inactivityMatchId.isNull() else it.inactivityMatchId eq row.inactivityMatchId) }
        } == 1
    }
    fun owns(row: PetSubscription, owner: String, now: Long): Boolean = db { database ->
        database.from(Pets).select(Pets.playerId).where { leased(row,owner) and (Pets.leaseUntil greater now) and (Pets.expiresAt greater now) }.map { it[Pets.playerId] }.isNotEmpty()
    }
    fun finish(row: PetSubscription, owner: String) = db { database ->
        database.update(Pets) {
            set(it.nickname,row.nickname); set(it.lastMatchId,row.lastMatchId); set(it.lastMatchTime,row.lastMatchTime)
            set(it.nextCheckAt,row.nextCheckAt); set(it.failures,row.failures); set(it.lastError,row.lastError)
            // A null/stale caller must not erase an acknowledgement whose commit result was lost.
            row.inactivityMatchId?.let { id -> set(it.inactivityMatchId,id) }
            set(it.leaseOwner,null as String?); set(it.leaseUntil,0L)
            where { leased(row,owner) }
        }
    }
    private fun leased(row: PetSubscription, owner: String) = (Pets.groupId eq row.groupId) and (Pets.playerId eq row.playerId) and
        (Pets.expiresAt eq row.expiresAt) and (Pets.leaseOwner eq owner)
    private fun subscription(row: QueryRowSet) = PetSubscription(row[Pets.groupId]!!,row[Pets.playerId]!!,row[Pets.nickname]!!,
        row[Pets.requesterId]!!,row[Pets.lastMatchId],row[Pets.lastMatchTime],row[Pets.nextCheckAt]!!,row[Pets.failures]!!,row[Pets.lastError],row[Pets.expiresAt]!!,
        row[Pets.inactivityMatchId],row[Pets.expiryNoticeNextAt] ?: 0L)
}

private object Pets : Table<Nothing>("group_pet") {
    val groupId = varchar("group_id").primaryKey()
    val playerId = long("player_id").primaryKey()
    val nickname = varchar("nickname")
    val requesterId = varchar("requester_id")
    val lastMatchId = long("last_match_id")
    val lastMatchTime = long("last_match_time")
    val nextCheckAt = long("next_check_at")
    val failures = int("failures")
    val lastError = varchar("last_error")
    val leaseOwner = varchar("lease_owner")
    val leaseUntil = long("lease_until")
    val expiresAt = long("expires_at")
    val inactivityMatchId = long("inactivity_match_id")
    val expiryNoticeNextAt = long("expiry_notice_next_at")
}
private object Migration : Table<Nothing>("group_pet_migration") {
    val source = varchar("source").primaryKey()
    val completedAt = long("completed_at")
}
