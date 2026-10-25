package cn.luorenmu.repository

import cn.luorenmu.common.util.DatabaseManager
import cn.luorenmu.common.database.insertIgnoring
import cn.luorenmu.repository.entity.EternalReturnNewsRecord
import cn.luorenmu.repository.table.EternalReturnNews
import cn.luorenmu.service.GameActivityVisibility.hasRedemptionCode
import io.github.oshai.kotlinlogging.KotlinLogging
import org.ktorm.database.Database
import org.ktorm.dsl.QueryRowSet
import org.ktorm.dsl.desc
import org.ktorm.dsl.eq
import org.ktorm.dsl.from
import org.ktorm.dsl.isNotNull
import org.ktorm.dsl.limit
import org.ktorm.dsl.map
import org.ktorm.dsl.or
import org.ktorm.dsl.orderBy
import org.ktorm.dsl.select
import org.ktorm.dsl.where
import java.time.LocalDateTime

/**
 * 官方新闻仓储层。
 *
 * 负责永恒轮回官方新闻处理结果的持久化，按 articleId 幂等去重。
 *
 * @author LoMu
 * Date 2026/8/9
 */
open class NewsRepository(private val dbManager: DatabaseManager) {
    private val logger = KotlinLogging.logger {}

    private fun <T> withDatabase(
        operationName: String,
        defaultValue: T,
        block: (Database) -> T,
    ): T {
        val database = dbManager.database
        return try {
            block(database)
        } catch (e: Exception) {
            logger.error(e) { "操作失败 [$operationName]: ${e.message}" }
            defaultValue
        }
    }

    /** 判断文章是否已入库（按 articleId）；查询失败时抛出，避免重复交给 AI。 */
    open fun isArticleExists(articleId: Int): Boolean {
        return dbManager.database
            .from(EternalReturnNews)
            .select(EternalReturnNews.articleId)
            .where { EternalReturnNews.articleId eq articleId }
            .map { row -> row[EternalReturnNews.articleId] }
            .firstOrNull() != null
    }

    /** 批量查询已入库的文章 ID；失败时不能当作尚未处理过任何文章。 */
    open fun findExistingArticleIds(): Set<Int> {
        return dbManager.database
            .from(EternalReturnNews)
            .select(EternalReturnNews.articleId)
            .map { row -> row[EternalReturnNews.articleId] }
            .filterNotNull()
            .toSet()
    }

    /** Push deduplication needs historical codes too, not just the latest article window. */
    open fun findRedemptionCodesForPush(): List<EternalReturnNewsRecord> =
        withDatabase("findRedemptionCodesForPush", emptyList()) { database ->
            database.from(EternalReturnNews).select()
                .where { EternalReturnNews.code.isNotNull() }
                .map { row -> mapRow(row) }.filter { it.hasRedemptionCode() }
        }

    /** 查询最近识别出的兑换码活动。 */
    open fun findLatestRedemptionCodes(limit: Int = 5): List<EternalReturnNewsRecord> {
        require(limit > 0) { "limit 必须大于 0" }

        return withDatabase("findLatestRedemptionCodes", emptyList()) { database ->
            database
                .from(EternalReturnNews)
                .select()
                .where { EternalReturnNews.code.isNotNull() }
                .orderBy(EternalReturnNews.processedAt.desc())
                .limit(limit)
                .map { row -> mapRow(row) }
                .filter { it.hasRedemptionCode() }
        }
    }

    /** 查询最近识别出的游戏活动。 */
    open fun findLatestGameActivities(limit: Int = 5): List<EternalReturnNewsRecord> {
        require(limit > 0) { "limit 必须大于 0" }

        return withDatabase("findLatestGameActivities", emptyList()) { database ->
            database
                .from(EternalReturnNews)
                .select()
                .where {
                    EternalReturnNews.eventStartTime.isNotNull() or
                        (EternalReturnNews.isGameActivity eq true) or
                        EternalReturnNews.code.isNotNull()
                }
                .orderBy(EternalReturnNews.processedAt.desc())
                .limit(limit)
                .map { row -> mapRow(row) }
        }
    }

    /** 按文章 ID 原子插入，重复扫描不得改写兑换码及首次发现时间。SQLite / PostgreSQL 通用。 */
    open fun insert(record: EternalReturnNewsRecord): Boolean {
        return withDatabase("insertNews", false) { database ->
            database.insertIgnoring(EternalReturnNews, EternalReturnNews.articleId) {
                set(it.articleId,record.articleId); set(it.title,record.title); set(it.thumbnailUrl,record.thumbnailUrl)
                set(it.createdAt,record.createdAt); set(it.contentText,record.contentText)
                set(it.eventStartTime,record.eventStartTime); set(it.eventEndTime,record.eventEndTime)
                set(it.isGameActivity,record.isGameActivity); set(it.isRedemptionCode,record.isRedemptionCode)
                set(it.code,record.code); set(it.reward,record.reward); set(it.note,record.note)
                set(it.startDate,record.startDate); set(it.endDate,record.endDate); set(it.processedAt,record.processedAt)
            } > 0
        }
    }

    private fun mapRow(row: QueryRowSet): EternalReturnNewsRecord {
        return EternalReturnNewsRecord(
            id = row[EternalReturnNews.id] ?: 0,
            articleId = row[EternalReturnNews.articleId] ?: 0,
            title = row[EternalReturnNews.title] ?: "",
            thumbnailUrl = row[EternalReturnNews.thumbnailUrl],
            createdAt = row[EternalReturnNews.createdAt],
            contentText = row[EternalReturnNews.contentText] ?: "",
            eventStartTime = row[EternalReturnNews.eventStartTime],
            eventEndTime = row[EternalReturnNews.eventEndTime],
            isGameActivity = row[EternalReturnNews.isGameActivity] ?: false,
            isRedemptionCode = row[EternalReturnNews.isRedemptionCode] ?: false,
            code = row[EternalReturnNews.code],
            reward = row[EternalReturnNews.reward],
            note = row[EternalReturnNews.note],
            startDate = row[EternalReturnNews.startDate],
            endDate = row[EternalReturnNews.endDate],
            processedAt = row[EternalReturnNews.processedAt] ?: LocalDateTime.now(),
        )
    }
}
