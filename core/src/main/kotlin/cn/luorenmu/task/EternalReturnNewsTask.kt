package cn.luorenmu.task

import cn.luorenmu.ai.news.NewsClassifier
import cn.luorenmu.repository.NewsRepository
import cn.luorenmu.repository.entity.EternalReturnNewsRecord
import cn.luorenmu.request.RequestManager
import cn.luorenmu.request.api.PakeApi
import cn.luorenmu.request.api.entity.module.CacheTime
import cn.luorenmu.request.api.entity.response.official.EternalReturnNews
import cn.luorenmu.request.api.impl.EternalReturnOfficialApi
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds

/**
 * 定时拉取官方新闻，解析 HTML 内容文本，AI 识别游戏活动，处理完成后写入数据库。
 *
 * @author LoMu
 * Date 2026/5/23 18:31
 */
class EternalReturnNewsTask internal constructor(
    private val newsRepository: NewsRepository,
    private val fetchArticles: suspend () -> List<EternalReturnNews.Article>,
    private val fetchHtml: suspend (String) -> String,
    private val classify: suspend (String, String) -> NewsClassifier.RedemptionCodeResult?,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    private val contentFetchDelayMillis: Long = CONTENT_FETCH_DELAY_MS,
) {
    constructor(classifier: NewsClassifier, newsRepository: NewsRepository) : this(
        newsRepository,
        { EternalReturnOfficialApi.Posts.GetNews.execute().articles },
        { fetchContentHtml(it) },
        classifier::classify,
    )

    private val log = KotlinLogging.logger {}
    private val seenIds = ConcurrentHashMap.newKeySet<Int>()
    private val scanMutex = Mutex()
    private var restoredIds = false
    private var pollingJob: Job? = null

    data class ParsedNews(
        val articleId: Int,
        val title: String,
        val thumbnailUrl: String? = null,
        val createdAt: LocalDateTime?,
        val contentText: String,
        val redemptionCode: NewsClassifier.RedemptionCodeResult? = null,
    )

    companion object {
        private const val CONTENT_FETCH_DELAY_MS = 500L
        private const val DEFAULT_ACTIVITY_WEEKS = 2L
    }

    /** 启动定时任务，每 30 分钟拉取一次。 */
    @Synchronized
    fun start(intervalMillis: Long = 1_800_000L) {
        require(intervalMillis > 0) { "扫描间隔必须大于 0" }
        if (pollingJob?.isActive == true) return
        pollingJob = scope.launch {
            while (isActive) {
                try {
                    scanOnce()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log.error(e) { "新闻拉取失败: ${e.message}" }
                }
                delay(intervalMillis.milliseconds)
            }
        }
    }


    /** 拉取新闻列表 → 筛选未读 → 获取正文 → 解析 HTML → 提取时间 → AI 分类。 */
    suspend fun fetchAndParseNews(): List<ParsedNews> = scanMutex.withLock { fetchUnprocessedNews() }

    private suspend fun fetchUnprocessedNews(): List<ParsedNews> {
        if (!restoredIds) {
            // 仅持久化成功的 ID 才是已完成；读取失败则中止本轮，不能当作空数据库。
            seenIds.addAll(newsRepository.findExistingArticleIds())
            restoredIds = true
        }
        val articles = fetchArticles().distinctBy { it.id }.filter { article ->
            !article.i18ns.zhCN?.title.isNullOrBlank() && article.id !in seenIds
        }

        log.debug { "新文章数量: ${articles.size}" }

        return articles.mapNotNull { article ->
            val result = parseArticle(article)
            // 每抓取一篇正文后间隔一段时间，避免频繁请求官方服务器
            delay(contentFetchDelayMillis.milliseconds)
            result
        }
    }

    private suspend fun parseArticle(article: EternalReturnNews.Article): ParsedNews? {
        val i18n = article.i18ns.zhCN ?: return null
        val contentLink = i18n.contentLink ?: return null

        return try {
            // 交给 AI 之前验证文章 ID 是否已入库，已入库则跳过，避免重复调用 AI
            if (newsRepository.isArticleExists(article.id)) {
                seenIds.add(article.id)
                log.debug { "文章已入库，跳过 AI 处理: ${i18n.title} (articleId=${article.id})" }
                return null
            }
            val html = fetchHtml(contentLink)
            val codeResult = classify(i18n.title, html) ?: run {
                log.warn { "文章尚未完成 AI 识别，保留重试: articleId=${article.id}" }
                return null
            }
            currentCoroutineContext().ensureActive()
            ParsedNews(
                articleId = article.id,
                title = i18n.title,
                thumbnailUrl = article.thumbnailUrl,
                createdAt = parseCreatedAt(article.createdAt),
                contentText = html,
                redemptionCode = codeResult,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.error(e) { "解析新闻内容失败: ${i18n.title} -> $contentLink" }
            null
        }
    }

    private fun saveNews(news: List<ParsedNews>): Int {
        var saved = 0
        news.forEach { item ->
            val inserted = newsRepository.insert(item.toRecord())
            if (inserted) {
                seenIds.add(item.articleId)
                saved++
                log.debug { "已入库文章: ${item.title}" }
            } else if (newsRepository.isArticleExists(item.articleId)) {
                // 其他扫描实例先入库时也视为完成，但不更新首次发现时间。
                seenIds.add(item.articleId)
            }
        }
        log.info { "新闻入库: 本次新增 $saved 篇" }
        return saved
    }

    internal suspend fun scanOnce(): Int = scanMutex.withLock { saveNews(fetchUnprocessedNews()) }

    internal fun ParsedNews.toRecord(now: LocalDateTime = LocalDateTime.now()): EternalReturnNewsRecord {
        val activity = redemptionCode
        val code = activity?.code.normalizeCode()
        val activityDetails = activity?.takeIf { it.isGameActivity || code != null }
        val startTime = activityDetails?.let { details ->
            details.startDate.toDateOrNull()
                ?.atTime(details.startHours.validHour(), details.startMinutes.validMinute())
                ?: now.toLocalDate().atStartOfDay()
        }
        val endTime = activityDetails?.let { details ->
            details.endDate.toDateOrNull()
                ?.atTime(details.endHours.validHour(), details.endMinutes.validMinute())
                ?: startTime!!.plusWeeks(DEFAULT_ACTIVITY_WEEKS)
        }
        val isActivity = activityDetails != null
        return EternalReturnNewsRecord(
            articleId = articleId,
            title = title,
            thumbnailUrl = thumbnailUrl,
            createdAt = createdAt,
            contentText = contentText,
            eventStartTime = startTime,
            eventEndTime = endTime,
            // 兼容旧版本：旧列继续同步写入，但新业务逻辑不再读取它们。
            isGameActivity = isActivity,
            isRedemptionCode = code != null,
            code = code,
            reward = activity?.reward,
            note = activity?.note,
            startDate = startTime?.toLocalDate()?.toString(),
            endDate = endTime?.toLocalDate()?.toString(),
            processedAt = now,
        )
    }

    private fun String?.toDateOrNull(): LocalDate? =
        this?.trim()?.takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }

    private fun Int.validHour(): Int = takeIf { it in 0..23 } ?: 0

    private fun Int.validMinute(): Int = takeIf { it in 0..59 } ?: 0

    private fun String?.normalizeCode(): String? {
        val value = this?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return value.takeUnless {
            it.lowercase() == "null" || it in setOf("无", "没有", "无兑换码", "无兑换码活动", "详见活动页面")
        }
    }

    private fun parseCreatedAt(raw: String?): LocalDateTime? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            OffsetDateTime.parse(raw)
                .atZoneSameInstant(ZoneId.systemDefault())
                .toLocalDateTime()
        }.getOrNull()
    }

}

private class ContentHtmlApi(
    htmlUrl: String,
) : PakeApi(
    url = htmlUrl,
    method = HttpMethod.Get,
    headers = mutableMapOf(
        "Accept-language" to "zh-CN,zh;q=0.9",
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
    ),
    cacheTime = CacheTime.NULL,
) {
    override var baseUrl: String = ""
}

private suspend fun fetchContentHtml(url: String): String {
    val fullUrl = if (url.startsWith("http")) url else "https://playeternalreturn.com$url"
    val api = ContentHtmlApi(fullUrl)
    return RequestManager.call(api).bodyAsText()
}
