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
class EternalReturnNewsTask(
    private val classifier: NewsClassifier,
    private val newsRepository: NewsRepository,
) {

    private val log = KotlinLogging.logger {}
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val seenIds = ConcurrentHashMap.newKeySet<Int>()

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
    }

    /** 启动定时任务，每 30 分钟拉取一次。 */
    fun start(intervalMillis: Long = 1_800_000L) {
        scope.launch {
            newsRepository.findExistingArticleIds().let { seenIds.addAll(it) }
            while (isActive) {
                try {
                    val news = fetchAndParseNews()
                    saveNews(news)
                } catch (e: Exception) {
                    log.error(e) { "新闻拉取失败: ${e.message}" }
                }
                delay(intervalMillis.milliseconds)
            }
        }
    }


    /** 拉取新闻列表 → 筛选未读 → 获取正文 → 解析 HTML → 提取时间 → AI 分类。 */
    suspend fun fetchAndParseNews(): List<ParsedNews> {
        val newsResponse = EternalReturnOfficialApi.Posts.GetNews.execute()
        val articles = newsResponse.articles.filter { article ->
            !article.i18ns.zhCN?.title.isNullOrBlank() && seenIds.add(article.id)
        }

        log.debug { "新文章数量: ${articles.size}" }

        return articles.mapNotNull { article ->
            val result = parseArticle(article)
            // 每抓取一篇正文后间隔一段时间，避免频繁请求官方服务器
            delay(CONTENT_FETCH_DELAY_MS.milliseconds)
            result
        }
    }

    private suspend fun parseArticle(article: EternalReturnNews.Article): ParsedNews? {
        val i18n = article.i18ns.zhCN ?: return null
        val contentLink = i18n.contentLink ?: return null

        return try {
            // 交给 AI 之前验证文章 ID 是否已入库，已入库则跳过，避免重复调用 AI
            if (newsRepository.isArticleExists(article.id)) {
                log.debug { "文章已入库，跳过 AI 处理: ${i18n.title} (articleId=${article.id})" }
                return null
            }
            val html = fetchContentHtml(contentLink)
            val codeResult = classifier.classify(i18n.title, html)
            ParsedNews(
                articleId = article.id,
                title = i18n.title,
                thumbnailUrl = article.thumbnailUrl,
                createdAt = parseCreatedAt(article.createdAt),
                contentText = html,
                redemptionCode = codeResult,
            )
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
                saved++
                log.debug { "已入库文章: ${item.title}" }
            }
        }
        log.info { "新闻入库: 本次新增 $saved 篇" }
        return saved
    }

    private fun ParsedNews.toRecord(): EternalReturnNewsRecord {
        val activity = redemptionCode
        return EternalReturnNewsRecord(
            articleId = articleId,
            title = title,
            thumbnailUrl = thumbnailUrl,
            createdAt = createdAt,
            contentText = contentText,
            isGameActivity = activity?.isGameActivity == true || activity?.isRedemptionCode == true,
            isRedemptionCode = activity?.isRedemptionCode == true,
            code = activity?.code,
            reward = activity?.reward,
            note = activity?.note,
            startDate = activity?.startDate,
            endDate = activity?.endDate,
        )
    }

    private fun parseCreatedAt(raw: String?): LocalDateTime? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            OffsetDateTime.parse(raw)
                .atZoneSameInstant(ZoneId.systemDefault())
                .toLocalDateTime()
        }.getOrNull()
    }

    private suspend fun fetchContentHtml(url: String): String {
        val fullUrl = if (url.startsWith("http")) url else "https://playeternalreturn.com$url"
        val api = ContentHtmlApi(fullUrl)
        return RequestManager.call(api).bodyAsText()
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
