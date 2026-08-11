package cn.luorenmu.repository

import cn.luorenmu.common.util.DatabaseManager
import cn.luorenmu.repository.entity.CommandUsageRecord
import cn.luorenmu.repository.entity.NicknameQueryRecord
import cn.luorenmu.repository.table.CommandUsages
import cn.luorenmu.repository.table.NicknameQueries
import io.github.oshai.kotlinlogging.KotlinLogging
import org.ktorm.database.Database
import org.ktorm.dsl.desc
import org.ktorm.dsl.eq
import org.ktorm.dsl.from
import org.ktorm.dsl.insert
import org.ktorm.dsl.limit
import org.ktorm.dsl.map
import org.ktorm.dsl.orderBy
import org.ktorm.dsl.plus
import org.ktorm.dsl.select
import org.ktorm.dsl.update
import org.ktorm.dsl.where
import java.time.LocalDateTime

/**
 * 统计信息仓储层，负责命令使用记录和昵称查询记录的持久化操作。
 *
 * 使用 Ktorm 数据库实现。所有公开方法均通过
 * [withDatabase] 统一处理数据库启用状态检查和异常处理。
 *
 * @author LoMu
 * Date 2026/5/1 18:33
 */
open class StatisticsRepository(private val dbManager: DatabaseManager) {
    private val logger = KotlinLogging.logger {}

    companion object {
        /** 默认热门昵称查询数量 */
        private const val DEFAULT_TOP_NICKNAMES_LIMIT = 10
    }

    /**
     * 统一的数据操作模板方法。
     *
     * 1. 检查数据库是否启用，未启用时返回 [defaultValue]
     * 2. 获取 Database 实例，为 null 时返回 [defaultValue]
     * 3. 执行 [block] 中的业务逻辑
     * 4. 捕获所有异常并记录日志，返回 [defaultValue]
     */
    private fun <T> withDatabase(
        operationName: String,
        defaultValue: T,
        block: (Database) -> T,
    ): T {
        if (!dbManager.isEnabled()) {
            logger.debug { "${dbManager.displayName()} 未启用，跳过操作: $operationName" }
            return defaultValue
        }
        val database = dbManager.database
        if (database == null) {
            logger.warn { "${dbManager.displayName()} Database 实例为 null，跳过操作: $operationName" }
            return defaultValue
        }
        return try {
            block(database)
        } catch (e: Exception) {
            logger.error(e) { "操作失败 [$operationName]: ${e.message}" }
            defaultValue
        }
    }

    /**
     * 记录一条命令使用记录。
     *
     * @param commandName 命令名称，不能为空或空白
     * @param nickname 触发命令的用户昵称（可选）
     */
    open fun recordCommandUsage(commandName: String, nickname: String? = null, groupId: String? = null, senderId: String? = null) {
        require(commandName.isNotBlank()) { "commandName 不能为空" }

        withDatabase(operationName = "recordCommandUsage", defaultValue = Unit) { database ->
            database.insert(CommandUsages) {
                set(it.commandName, commandName)
                set(it.nickname, nickname)
                set(it.groupId, groupId)
                set(it.senderId, senderId)
                set(it.timestamp, LocalDateTime.now())
            }
            logger.debug { "记录命令使用: $commandName, nickname: $nickname, group: $groupId, sender: $senderId" }
        }
    }

    open fun listCommandUsages(limit: Int = 100): List<CommandUsageRecord> {
        require(limit > 0) { "limit 必须大于 0" }

        return withDatabase(operationName = "listCommandUsages", defaultValue = emptyList()) { database ->
            database
                .from(CommandUsages)
                .select()
                .orderBy(CommandUsages.timestamp.desc())
                .limit(limit)
                .map { row ->
                    CommandUsageRecord(
                        id = row[CommandUsages.id] ?: 0,
                        commandName = row[CommandUsages.commandName] ?: "",
                        nickname = row[CommandUsages.nickname],
                        groupId = row[CommandUsages.groupId],
                        senderId = row[CommandUsages.senderId],
                        timestamp = (row[CommandUsages.timestamp] ?: LocalDateTime.now()).toString(),
                    )
                }
        }
    }

    open fun updateCommandUsage(
        id: Long,
        commandName: String,
        nickname: String?,
        groupId: String?,
        senderId: String?,
    ): Boolean {
        require(id > 0) { "id 必须大于 0" }
        require(commandName.isNotBlank()) { "commandName 不能为空" }

        return withDatabase(operationName = "updateCommandUsage", defaultValue = false) { database ->
            database.update(CommandUsages) {
                set(it.commandName, commandName)
                set(it.nickname, nickname)
                set(it.groupId, groupId)
                set(it.senderId, senderId)
                where { it.id eq id }
            } > 0
        }
    }

    /**
     * 原子性地增加昵称查询计数。
     *
     * 使用 PostgreSQL 的 `INSERT ... ON CONFLICT ... DO UPDATE` 实现 upsert，
     * 避免"先查再插"带来的竞态条件。
     *
     * @param nickname 要查询的昵称，不能为空或空白
     */
    open fun incrementNicknameQueryCount(nickname: String) {
        require(nickname.isNotBlank()) { "nickname 不能为空" }

        withDatabase(operationName = "incrementNicknameQueryCount", defaultValue = Unit) { database ->
            val now = LocalDateTime.now()

            val updatedRows = database.update(NicknameQueries) {
                set(it.queryCount, it.queryCount + 1)
                set(it.lastQueryAt, now)
                where { it.nickname eq nickname }
            }

            if (updatedRows == 0) {
                try {
                    database.insert(NicknameQueries) {
                        set(it.nickname, nickname)
                        set(it.queryCount, 1)
                        set(it.firstQueryAt, now)
                        set(it.lastQueryAt, now)
                    }
                } catch (_: Exception) {
                    // 并发插入冲突，重试 update
                    database.update(NicknameQueries) {
                        set(it.queryCount, it.queryCount + 1)
                        set(it.lastQueryAt, now)
                        where { it.nickname eq nickname }
                    }
                }
            }

            logger.debug { "更新昵称查询计数: $nickname" }
        }
    }

    /**
     * 获取指定昵称的查询次数。
     *
     * @param nickname 要查询的昵称，不能为空或空白
     * @return 查询次数，如果记录不存在则返回 0
     */
    open fun getNicknameQueryCount(nickname: String): Long {
        require(nickname.isNotBlank()) { "nickname 不能为空" }

        return withDatabase(operationName = "getNicknameQueryCount", defaultValue = 0L) { database ->
            database
                .from(NicknameQueries)
                .select(NicknameQueries.queryCount)
                .where { NicknameQueries.nickname eq nickname }
                .map { row -> row[NicknameQueries.queryCount] ?: 0L }
                .firstOrNull() ?: 0L
        }
    }

    /**
     * 获取查询次数最多的昵称列表（热门昵称）。
     *
     * @param limit 返回结果数量上限，必须大于 0，默认为 [DEFAULT_TOP_NICKNAMES_LIMIT]
     * @return 按查询次数降序排列的昵称记录列表
     */
    open fun getTopQueriedNicknames(limit: Int = DEFAULT_TOP_NICKNAMES_LIMIT): List<NicknameQueryRecord> {
        require(limit > 0) { "limit 必须大于 0，当前值: $limit" }

        return withDatabase(
            operationName = "getTopQueriedNicknames",
            defaultValue = emptyList(),
        ) { database ->
            database
                .from(NicknameQueries)
                .select()
                .orderBy(NicknameQueries.queryCount.desc())
                .limit(limit)
                .map { row ->
                    NicknameQueryRecord(
                        id = row[NicknameQueries.id] ?: 0,
                        nickname = row[NicknameQueries.nickname] ?: "",
                        queryCount = row[NicknameQueries.queryCount] ?: 0,
                        firstQueryAt = row[NicknameQueries.firstQueryAt] ?: LocalDateTime.now(),
                        lastQueryAt = row[NicknameQueries.lastQueryAt] ?: LocalDateTime.now(),
                    )
                }
        }
    }

}
