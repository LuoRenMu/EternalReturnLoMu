package cn.luorenmu.task

import cn.luorenmu.ai.NewsClassifier
import cn.luorenmu.request.RequestManager
import cn.luorenmu.request.api.PakeApi
import cn.luorenmu.request.api.entity.module.CacheTime
import cn.luorenmu.request.api.impl.EternalReturnOfficialApi
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.*
import java.time.LocalDate
import kotlin.time.Duration.Companion.milliseconds
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

/**
 * 定时拉取官方新闻，解析 HTML 内容文本，AI 识别兑换码活动。
 *
 * @author LoMu
 * Date 2026/5/23 18:31
 */
class EternalReturnNewsTask(
    private val classifier: NewsClassifier,
) {

    private val log = KotlinLogging.logger {}
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val seenIds = ConcurrentHashMap.newKeySet<Int>()

    data class ParsedNews(
        val title: String,
        val createdAt: String,
        val contentText: String,
        val eventStartTime: LocalDateTime?,
        val eventEndTime: LocalDateTime?,
        val redemptionCode: NewsClassifier.RedemptionCodeResult? = null,
    ) {
        val isEventActive: Boolean get() = eventEndTime?.let { LocalDateTime.now() < it } ?: false
        val isRedemptionCode: Boolean get() = redemptionCode?.isRedemptionCode == true
    }

    companion object {
        private val DATE_REGEX = Regex(
            """(\d{4})[年/.-](\d{1,2})[月/.-](\d{1,2})日?(?:\([^)]+\))?(?:\s*(\d{1,2}):(\d{2}))?"""
        )
        private val DATE_NO_YEAR_REGEX = Regex(
            """(?<!\d)(\d{1,2})[月/.-](\d{1,2})日?(?:\([^)]+\))?(?:\s*(\d{1,2}):(\d{2}))?(?!\d)"""
        )
        private val currentYear = LocalDate.now().year
    }

    /** 启动定时任务，每小时拉取一次。 */
    fun start(intervalMillis: Long = 3_600_000L) {
        scope.launch {
            while (isActive) {
                try {
                    val news = fetchAndParseNews()
                    val activeEvents = news.filter { it.isEventActive }
                    val codes = news.filter { it.isRedemptionCode }
                    log.info { "新闻: 新 ${news.size} 篇, 活动 ${activeEvents.size} 个, 兑换码 ${codes.size} 个" }
                    codes.forEach { item ->
                        log.info { "兑换码: ${item.redemptionCode?.code} | ${item.redemptionCode?.reward} | ${item.title}" }
                    }
                } catch (e: Exception) {
                    log.error(e) { "新闻拉取失败: ${e.message}" }
                }
                delay(intervalMillis.milliseconds)
            }
        }
    }

    fun stop() {
        scope.cancel()
    }

    /** 拉取新闻列表 → 筛选未读 → 获取正文 → 解析 HTML → 提取时间 → AI 分类。 */
    suspend fun fetchAndParseNews(): List<ParsedNews> {
        val newsResponse = EternalReturnOfficialApi.Posts.GetNews.execute()
        val articles = newsResponse.articles.filter { article ->
            !article.i18ns.zhCN?.title.isNullOrBlank() && seenIds.add(article.id)
        }

        log.debug { "新文章数量: ${articles.size}" }

        return articles.mapNotNull { article ->
            val i18n = article.i18ns.zhCN ?: return@mapNotNull null
            val contentLink = i18n.contentLink ?: return@mapNotNull null

            try {
                val html = fetchContentHtml(contentLink)
                val text = parseArticleContent(html)
                val times = parseEventTimes(text)
                val codeResult = classifier.classify(i18n.title, text)
                ParsedNews(
                    title = i18n.title,
                    createdAt = article.createdAt,
                    contentText = text,
                    eventStartTime = times?.first,
                    eventEndTime = times?.second,
                    redemptionCode = codeResult,
                )
            } catch (e: Exception) {
                log.error(e) { "解析新闻内容失败: ${i18n.title} -> $contentLink" }
                null
            }
        }
    }

    private suspend fun fetchContentHtml(url: String): String {
        val fullUrl = if (url.startsWith("http")) url else "https://playeternalreturn.com$url"
        val api = ContentHtmlApi(fullUrl)
        return RequestManager.call(api).bodyAsText()
    }

    // ── HTML 解析 ────────────────────────────────────────────────

    fun parseArticleContent(html: String): String {
        val divRegex = Regex(
            """<div[^>]*class="er-article-detail__content er-article-content fr-view"[^>]*>(.*?)</div>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        val raw = divRegex.find(html)?.groupValues?.get(1) ?: return ""

        return raw
            .replace(Regex("""</(p|li|h[1-6]|div|ul|ol|tr)[^>]*>""", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("""<[^>]+>"""), "")
            .replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace(Regex("""\n{3,}"""), "\n\n")
            .trim()
    }

    // ── 时间解析 ────────────────────────────────────────────────

    fun parseEventTimes(text: String): Pair<LocalDateTime?, LocalDateTime?>? {
        val dates = mutableListOf<LocalDateTime>()

        DATE_REGEX.findAll(text).forEach { match ->
            val (y, m, d, h, min) = match.groupValues.drop(1)
            dates += LocalDateTime.of(y.toInt(), m.toInt(), d.toInt(), h.toIntOrNull() ?: 0, min.toIntOrNull() ?: 0)
        }

        DATE_NO_YEAR_REGEX.findAll(text).forEach { match ->
            val (m, d, h, min) = match.groupValues.drop(1)
            dates += LocalDateTime.of(currentYear, m.toInt(), d.toInt(), h.toIntOrNull() ?: 0, min.toIntOrNull() ?: 0)
        }

        val sorted = dates.distinct().sorted()
        if (sorted.isEmpty()) return null

        val now = LocalDateTime.now()
        val futureDates = sorted.filter { it > now }
        if (futureDates.isEmpty()) return null

        return when {
            sorted.size >= 2 -> sorted.first() to sorted.last()
            else -> null to sorted.first()
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
}
