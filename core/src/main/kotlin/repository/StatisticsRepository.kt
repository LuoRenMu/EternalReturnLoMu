package cn.luorenmu.repository

import cn.luorenmu.common.util.MongoDBManager
import cn.luorenmu.repository.entity.CommandUsageRecord
import cn.luorenmu.repository.entity.NicknameQueryRecord
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Sorts
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import java.time.LocalDateTime

/**
 * 统计信息仓储层，负责命令使用记录和昵称查询记录的持久化操作。
 *
 * 所有公开方法均通过 [withDatabase] 统一处理 MongoDB 启用状态检查和异常处理，
 * 避免重复的样板代码。
 *
 * @author LoMu
 * Date 2026/5/1 18:33
 */
class StatisticsRepository(private val mongoManager: MongoDBManager) {
    private val logger = KotlinLogging.logger {}

    companion object {
        /** 命令使用记录集合名称 */
        private const val COLLECTION_COMMAND_USAGE = "command_usage"

        /** 昵称查询记录集合名称 */
        private const val COLLECTION_NICKNAME_QUERIES = "nickname_queries"

        /** 默认热门昵称查询数量 */
        private const val DEFAULT_TOP_NICKNAMES_LIMIT = 10

        /** 默认命令使用统计查询数量 */
        private const val DEFAULT_COMMAND_STATS_LIMIT = 100
    }

    /**
     * 统一的数据操作模板方法。
     *
     * 1. 检查 MongoDB 是否启用，未启用时返回 [defaultValue]
     * 2. 获取数据库实例，为 null 时返回 [defaultValue]
     * 3. 执行 [block] 中的业务逻辑
     * 4. 捕获所有异常并记录日志，返回 [defaultValue]
     *
     * @param operationName 操作名称，用于日志标识
     * @param defaultValue 操作失败或不可用时的默认返回值
     * @param block 实际的数据操作代码块，接收 [MongoDatabase] 参数
     * @return block 的执行结果，或失败时的 defaultValue
     */
    private suspend fun <T> withDatabase(
        operationName: String,
        defaultValue: T,
        block: suspend (MongoDatabase) -> T,
    ): T {
        if (!mongoManager.isEnabled()) {
            logger.debug { "MongoDB 未启用，跳过操作: $operationName" }
            return defaultValue
        }
        val database = mongoManager.database
        if (database == null) {
            logger.warn { "MongoDB 数据库实例为 null，跳过操作: $operationName" }
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
    suspend fun recordCommandUsage(commandName: String, nickname: String? = null) {
        require(commandName.isNotBlank()) { "commandName 不能为空" }

        withDatabase(operationName = "recordCommandUsage", defaultValue = Unit) { database ->
            val collection: MongoCollection<CommandUsageRecord> =
                database.getCollection(COLLECTION_COMMAND_USAGE)

            val record = CommandUsageRecord(
                commandName = commandName,
                nickname = nickname,
                timestamp = LocalDateTime.now(),
            )
            collection.insertOne(record)
            logger.debug { "记录命令使用: $commandName, nickname: $nickname" }
        }
    }

    /**
     * 原子性地增加昵称查询计数。
     *
     * 使用 MongoDB 的 `upsert` 机制，避免"先查再插"带来的竞态条件。
     * 如果记录不存在，会自动创建新记录并将 [firstQueryAt] 和 [lastQueryAt] 设为当前时间。
     *
     * @param nickname 要查询的昵称，不能为空或空白
     */
    suspend fun incrementNicknameQueryCount(nickname: String) {
        require(nickname.isNotBlank()) { "nickname 不能为空" }

        withDatabase(operationName = "incrementNicknameQueryCount", defaultValue = Unit) { database ->
            val collection: MongoCollection<NicknameQueryRecord> =
                database.getCollection(COLLECTION_NICKNAME_QUERIES)

            val now = LocalDateTime.now()
            val filter = Filters.eq("nickname", nickname)
            val update = Updates.combine(
                Updates.inc("queryCount", 1),
                Updates.set("lastQueryAt", now),
                Updates.setOnInsert("firstQueryAt", now),
            )

            collection.updateOne(filter, update, com.mongodb.client.model.UpdateOptions().upsert(true))
            logger.debug { "更新昵称查询计数: $nickname" }
        }
    }

    /**
     * 获取指定昵称的查询次数。
     *
     * @param nickname 要查询的昵称，不能为空或空白
     * @return 查询次数，如果记录不存在则返回 0
     */
    suspend fun getNicknameQueryCount(nickname: String): Long {
        require(nickname.isNotBlank()) { "nickname 不能为空" }

        return withDatabase(operationName = "getNicknameQueryCount", defaultValue = 0L) { database ->
            val collection: MongoCollection<NicknameQueryRecord> =
                database.getCollection(COLLECTION_NICKNAME_QUERIES)

            val filter = Filters.eq("nickname", nickname)
            val record = collection.find(filter).firstOrNull()
            record?.queryCount ?: 0L
        }
    }

    /**
     * 获取查询次数最多的昵称列表（热门昵称）。
     *
     * @param limit 返回结果数量上限，必须大于 0，默认为 [DEFAULT_TOP_NICKNAMES_LIMIT]
     * @return 按查询次数降序排列的昵称记录列表
     */
    suspend fun getTopQueriedNicknames(limit: Int = DEFAULT_TOP_NICKNAMES_LIMIT): List<NicknameQueryRecord> {
        require(limit > 0) { "limit 必须大于 0，当前值: $limit" }

        return withDatabase(
            operationName = "getTopQueriedNicknames",
            defaultValue = emptyList(),
        ) { database ->
            val collection: MongoCollection<NicknameQueryRecord> =
                database.getCollection(COLLECTION_NICKNAME_QUERIES)

            collection.find()
                .sort(Sorts.descending("queryCount"))
                .limit(limit)
                .toList()
        }
    }

    /**
     * 获取命令使用统计记录。
     *
     * @param commandName 可选的命令名称过滤条件，为 null 时返回所有命令的记录
     * @param limit 返回结果数量上限，必须大于 0，默认为 [DEFAULT_COMMAND_STATS_LIMIT]
     * @return 按时间戳降序排列的命令使用记录列表
     */
    suspend fun getCommandUsageStats(
        commandName: String? = null,
        limit: Int = DEFAULT_COMMAND_STATS_LIMIT,
    ): List<CommandUsageRecord> {
        require(limit > 0) { "limit 必须大于 0，当前值: $limit" }

        return withDatabase(
            operationName = "getCommandUsageStats",
            defaultValue = emptyList(),
        ) { database ->
            val collection: MongoCollection<CommandUsageRecord> =
                database.getCollection(COLLECTION_COMMAND_USAGE)

            val filter = commandName?.let { Filters.eq("commandName", it) }
                ?: Filters.empty()

            collection.find(filter)
                .sort(Sorts.descending("timestamp"))
                .limit(limit)
                .toList()
        }
    }
}