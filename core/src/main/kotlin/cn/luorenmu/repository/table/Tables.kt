package cn.luorenmu.repository.table

import org.ktorm.schema.Table
import org.ktorm.schema.long
import org.ktorm.schema.varchar
import org.ktorm.schema.datetime

/**
 * Ktorm 表定义 —— 命令使用记录
 *
 * @author LoMu
 * Date 2026/6/2
 */
object CommandUsages : Table<Nothing>("command_usage") {
    val id = long("id").primaryKey()
    val commandName = varchar("command_name")
    val nickname = varchar("nickname")
    val groupId = varchar("group_id")
    val senderId = varchar("sender_id")
    val timestamp = datetime("timestamp")
}

/**
 * Ktorm 表定义 —— 昵称查询记录
 *
 * @author LoMu
 * Date 2026/6/2
 */
object NicknameQueries : Table<Nothing>("nickname_queries") {
    val id = long("id").primaryKey()
    val nickname = varchar("nickname")
    val queryCount = long("query_count")
    val firstQueryAt = datetime("first_query_at")
    val lastQueryAt = datetime("last_query_at")
}

/**
 * Ktorm 表定义 —— 玩家别名
 *
 * scope: global / group / personal
 *
 * @author LoMu
 * Date 2026/6/2
 */
object PlayerAliases : Table<Nothing>("player_aliases") {
    val id = long("id").primaryKey()
    val aliasName = varchar("alias_name")
    val actualNickname = varchar("actual_nickname")
    val scope = varchar("scope")
    val groupId = varchar("group_id")
    val userId = varchar("user_id")
    val createdBy = varchar("created_by")
    val createdAt = datetime("created_at")
}
