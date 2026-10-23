package cn.luorenmu.plugins.test

import java.sql.Connection

internal data class BiliSubscription(val group: String, val room: Long, val status: Int, val session: String)

/** Uses the host's configured PostgreSQL/SQLite pool; never creates a private JSON database. */
internal class BiliLiveRepository(private val connection: ((Connection) -> Unit) -> Unit) {
    private fun <T> db(block: (Connection) -> T): T {
        var result: Result<T>? = null
        connection { c ->
            c.createStatement().use { it.execute("""CREATE TABLE IF NOT EXISTS test_bili_live (
                group_id TEXT NOT NULL, room_id BIGINT NOT NULL, live_status INTEGER NOT NULL,
                live_session TEXT NOT NULL, PRIMARY KEY(group_id,room_id))""") }
            result = runCatching { block(c) }
        }
        return checkNotNull(result).getOrThrow()
    }
    fun rows(): List<BiliSubscription> = db { c ->
        c.createStatement().use { s -> s.executeQuery("SELECT * FROM test_bili_live ORDER BY room_id,group_id").use { r ->
            buildList { while (r.next()) add(BiliSubscription(r.getString("group_id"), r.getLong("room_id"),
                r.getInt("live_status"), r.getString("live_session"))) }
        } }
    }
    fun add(group: String, room: BiliRoom): Boolean = db { c ->
        c.prepareStatement("INSERT INTO test_bili_live VALUES (?,?,?,?) ON CONFLICT(group_id,room_id) DO NOTHING").use {
            it.setString(1,group); it.setLong(2,room.id); it.setInt(3,room.status); it.setString(4,room.session)
            it.executeUpdate() == 1
        }
    }
    fun remove(group: String, room: Long): Boolean = db { c ->
        c.prepareStatement("DELETE FROM test_bili_live WHERE group_id=? AND room_id=?").use {
            it.setString(1,group); it.setLong(2,room); it.executeUpdate() == 1
        }
    }
    fun advance(old: BiliSubscription, room: BiliRoom): Boolean = db { c ->
        c.prepareStatement("""UPDATE test_bili_live SET live_status=?,live_session=?
            WHERE group_id=? AND room_id=? AND live_status=? AND live_session=?""").use {
            it.setInt(1,room.status); it.setString(2, if (room.status == 1) room.session else old.session)
            it.setString(3,old.group); it.setLong(4,old.room); it.setInt(5,old.status); it.setString(6,old.session)
            it.executeUpdate() == 1
        }
    }
}
