package cn.luorenmu.repository

import cn.luorenmu.common.util.DatabaseManager
import cn.luorenmu.repository.entity.AliasScope
import cn.luorenmu.repository.entity.PlayerAlias
import cn.luorenmu.repository.table.PlayerAliases
import io.github.oshai.kotlinlogging.KotlinLogging
import org.ktorm.database.Database
import org.ktorm.dsl.and
import org.ktorm.dsl.delete
import org.ktorm.dsl.desc
import org.ktorm.dsl.eq
import org.ktorm.dsl.from
import org.ktorm.dsl.insert
import org.ktorm.dsl.isNotNull
import org.ktorm.dsl.isNull
import org.ktorm.dsl.limit
import org.ktorm.dsl.map
import org.ktorm.dsl.or
import org.ktorm.dsl.orderBy
import org.ktorm.dsl.select
import org.ktorm.dsl.update
import org.ktorm.dsl.where
import java.time.LocalDateTime

/**
 * 玩家别名仓储层。
 *
 * 别名 scope 分三级：
 * - [AliasScope.GLOBAL]   全局可用
 * - [AliasScope.GROUP]    限定群聊
 * - [AliasScope.PERSONAL] 限定个人
 *
 * 解析优先级：personal > group > global
 *
 * @author LoMu
 * Date 2026/6/2
 */
open class PlayerAliasRepository(private val dbManager: DatabaseManager) {
    private val logger = KotlinLogging.logger {}

    companion object {
        private const val DEFAULT_LIST_LIMIT = 50
    }

    private fun <T> withDatabase(
        operationName: String,
        defaultValue: T,
        block: (Database) -> T,
    ): T {
        if (!dbManager.isEnabled()) {
            logger.debug { "PostgreSQL 未启用，跳过操作: $operationName" }
            return defaultValue
        }
        val database = dbManager.database ?: run {
            logger.warn { "Database 实例为 null，跳过操作: $operationName" }
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
     * 设置别名（upsert）。
     * 同一 scope 内 aliasName 唯一；冲突时更新 actualNickname。
     */
    open fun setAlias(
        alias: String,
        actualNickname: String,
        scope: AliasScope = AliasScope.GROUP,
        groupId: String? = null,
        userId: String? = null,
        createdBy: String,
    ) {
        require(alias.isNotBlank()) { "alias 不能为空" }
        require(actualNickname.isNotBlank()) { "actualNickname 不能为空" }

        withDatabase("setAlias", Unit) { database ->
            val updated = updateExisting(database, alias, actualNickname, scope, groupId, userId)

            if (updated == 0) {
                try {
                    database.insert(PlayerAliases) {
                        set(it.aliasName, alias)
                        set(it.actualNickname, actualNickname)
                        set(it.scope, scope.value)
                        set(it.groupId, groupId)
                        set(it.userId, userId)
                        set(it.createdBy, createdBy)
                        set(it.createdAt, LocalDateTime.now())
                    }
                } catch (_: Exception) {
                    updateExisting(database, alias, actualNickname, scope, groupId, userId)
                }
            }
            logger.debug { "别名已设置: $alias -> $actualNickname (scope=${scope.value})" }
        }
    }

    private fun updateExisting(
        database: Database,
        alias: String,
        actualNickname: String,
        scope: AliasScope,
        groupId: String?,
        userId: String?,
    ): Int {
        return database.update(PlayerAliases) {
            set(it.actualNickname, actualNickname)
            where {
                (it.aliasName eq alias) and (it.scope eq scope.value) and
                        scopeIdMatch(it, scope, groupId, userId)
            }
        }
    }

    /**
     * 解析别名 → 真实昵称。
     * 优先级：personal > group > global
     *
     * @return 真实昵称，若未匹配到别名则返回 null
     */

    open fun resolveAlias(
        alias: String,
        groupId: String?,
        userId: String?,
    ): String? {
        require(alias.isNotBlank()) { "alias 不能为空" }

        return withDatabase("resolveAlias", null) { database ->
            val scopes = listOf(
                Triple(AliasScope.PERSONAL, userId, "user_id"),
                Triple(AliasScope.GROUP, groupId, "group_id"),
                Triple(AliasScope.GLOBAL, null, null),
            )

            for ((scope, scopeId, _) in scopes) {
                val result = database
                    .from(PlayerAliases)
                    .select(PlayerAliases.actualNickname)
                    .where {
                        (PlayerAliases.aliasName eq alias) and
                                (PlayerAliases.scope eq scope.value) and
                                scopeNullSafeMatch(scope, scopeId)
                    }
                    .limit(1)
                    .map { row -> row[PlayerAliases.actualNickname] }
                    .firstOrNull()

                if (result != null) return@withDatabase result
            }
            null
        }
    }

    /**
     * 删除别名。
     */
    open fun deleteAlias(
        alias: String,
        scope: AliasScope,
        groupId: String? = null,
        userId: String? = null,
    ): Boolean {
        return withDatabase("deleteAlias", false) { database ->
            val rows = database.delete(PlayerAliases) {
                (it.aliasName eq alias) and (it.scope eq scope.value) and
                        scopeIdMatch(it, scope, groupId, userId)
            }
            rows > 0
        }
    }

    /**
     * 列出别名。
     *
     * @param scope 过滤 scope，为 null 时返回用户可见的所有别名
     * @param groupId 群聊上下文
     * @param userId 用户上下文
     */
    open fun listAliases(
        scope: AliasScope? = null,
        groupId: String? = null,
        userId: String? = null,
        limit: Int = DEFAULT_LIST_LIMIT,
    ): List<PlayerAlias> {
        require(limit > 0) { "limit 必须大于 0" }

        return withDatabase("listAliases", emptyList()) { database ->
            database
                .from(PlayerAliases)
                .select()
                .where {
                    when {
                        scope != null -> PlayerAliases.scope eq scope.value
                        else -> {
                            (PlayerAliases.scope eq AliasScope.GLOBAL.value) or
                                    ((PlayerAliases.scope eq AliasScope.GROUP.value) and
                                            (if (groupId != null) PlayerAliases.groupId eq groupId else PlayerAliases.groupId.isNotNull())) or
                                    ((PlayerAliases.scope eq AliasScope.PERSONAL.value) and
                                            (if (userId != null) PlayerAliases.userId eq userId else PlayerAliases.userId.isNotNull()))
                        }
                    }
                }
                .orderBy(PlayerAliases.scope.desc(), PlayerAliases.createdAt.desc())
                .limit(limit)
                .map { row -> mapRow(row) }
        }
    }

    private fun scopeIdMatch(
        it: PlayerAliases,
        scope: AliasScope,
        groupId: String?,
        userId: String?,
    ) = when (scope) {
        AliasScope.GLOBAL -> it.groupId.isNull() and it.userId.isNull()
        AliasScope.GROUP -> if (groupId != null) it.groupId eq groupId else it.groupId.isNotNull()
        AliasScope.PERSONAL -> if (userId != null) it.userId eq userId else it.userId.isNotNull()
    }

    private fun scopeNullSafeMatch(
        scope: AliasScope,
        scopeId: String?,
    ) = when (scope) {
        AliasScope.GLOBAL -> PlayerAliases.groupId.isNull() and PlayerAliases.userId.isNull()
        AliasScope.GROUP -> if (scopeId != null) PlayerAliases.groupId eq scopeId else PlayerAliases.groupId.isNotNull()
        AliasScope.PERSONAL -> if (scopeId != null) PlayerAliases.userId eq scopeId else PlayerAliases.userId.isNotNull()
    }

    private fun mapRow(row: org.ktorm.dsl.QueryRowSet): PlayerAlias {
        val scopeStr = row[PlayerAliases.scope] ?: "group"
        return PlayerAlias(
            id = row[PlayerAliases.id] ?: 0,
            alias = row[PlayerAliases.aliasName] ?: "",
            actualNickname = row[PlayerAliases.actualNickname] ?: "",
            scope = AliasScope.from(scopeStr),
            groupId = row[PlayerAliases.groupId],
            userId = row[PlayerAliases.userId],
            createdBy = row[PlayerAliases.createdBy] ?: "",
            createdAt = row[PlayerAliases.createdAt] ?: LocalDateTime.now(),
        )
    }
}
