package cn.luorenmu.task

import cn.luorenmu.ai.news.NewsClassifier.RedemptionCodeResult
import cn.luorenmu.common.util.DatabaseBackend
import cn.luorenmu.common.util.DatabaseManager
import cn.luorenmu.repository.NewsRepository
import cn.luorenmu.repository.entity.EternalReturnNewsRecord
import cn.luorenmu.request.api.entity.response.official.EternalReturnNews
import kotlinx.coroutines.*
import java.nio.file.Files
import java.time.LocalDateTime
import kotlin.test.*

class EternalReturnNewsDeduplicationTest {
    @Test
    fun `article ID survives duplicate list entries rescans edits and restart`() = runBlocking {
        Fixture().use { fixture ->
            var articles = listOf(article(3867), article(3867))
            var classifications = 0
            fun newTask() = fixture.task({ articles }) {
                classifications++
                RedemptionCodeResult(code = "ER-3867")
            }
            val task = newTask()
            assertEquals(1, task.scanOnce())
            val original = fixture.repository.findLatestRedemptionCodes().single()
            articles = listOf(article(3867).copy(i18ns = EternalReturnNews.I18ns(
                zhCN = EternalReturnNews.LocaleContent("zh_CN", "修改后的标题", contentLink = "/edited"),
            )))
            assertEquals(0, task.scanOnce())
            assertEquals(0, newTask().scanOnce())
            assertEquals(1, classifications, "same article must not invoke AI again, even after restart")
            assertEquals(original, fixture.repository.findLatestRedemptionCodes().single(),
                "repeat scans must not refresh the code's first discovery time")
        }
    }

    @Test
    fun `different article IDs with identical titles are still scanned`() = runBlocking {
        Fixture().use { fixture ->
            var classifications = 0
            val task = fixture.task({ listOf(article(3867), article(3868)) }) {
                classifications++
                RedemptionCodeResult(code = "ER-$classifications")
            }
            assertEquals(2, task.scanOnce())
            assertEquals(setOf(3867, 3868), fixture.repository.findExistingArticleIds())
            assertEquals(2, classifications)
        }
    }

    @Test
    fun `articles without a redemption code are also recorded as completed`() = runBlocking {
        Fixture().use { fixture ->
            var attempts = 0
            val task = fixture.task { attempts++; RedemptionCodeResult(isGameActivity = false) }
            assertEquals(1, task.scanOnce())
            assertEquals(0, task.scanOnce())
            assertEquals(1, attempts)
            assertEquals(setOf(3867), fixture.repository.findExistingArticleIds())
            assertTrue(fixture.repository.findLatestRedemptionCodes().isEmpty())
        }
    }

    @Test
    fun `failed initial ID lookup aborts scanning before AI and retries later`() = runBlocking {
        Fixture().use { fixture ->
            fixture.sql("ALTER TABLE eternal_return_news RENAME TO temporarily_unavailable_news")
            var attempts = 0
            val task = fixture.task { attempts++; RedemptionCodeResult(code = "ER-3867") }
            assertFailsWith<Exception> { task.scanOnce() }
            assertEquals(0, attempts)
            fixture.sql("ALTER TABLE temporarily_unavailable_news RENAME TO eternal_return_news")
            assertEquals(1, task.scanOnce())
            assertEquals(1, attempts)
        }
    }

    @Test
    fun `failed per article ID lookup never falls through to AI`() = runBlocking {
        Fixture().use { fixture ->
            var firstFetch = true
            var attempts = 0
            val task = fixture.task(fetchArticles = {
                if (firstFetch) {
                    firstFetch = false
                    fixture.sql("ALTER TABLE eternal_return_news RENAME TO temporarily_unavailable_news")
                }
                listOf(article(3867))
            }) { attempts++; RedemptionCodeResult(code = "ER-3867") }
            assertEquals(0, task.scanOnce())
            assertEquals(0, attempts)
            fixture.sql("ALTER TABLE temporarily_unavailable_news RENAME TO eternal_return_news")
            assertEquals(1, task.scanOnce())
            assertEquals(1, attempts)
        }
    }

    @Test
    fun `cancellation propagates and does not consume the article ID`() = runBlocking {
        Fixture().use { fixture ->
            var attempts = 0
            val task = fixture.task {
                if (++attempts == 1) throw CancellationException("cancel scan")
                RedemptionCodeResult(code = "ER-3867")
            }
            assertFailsWith<CancellationException> { task.scanOnce() }
            assertFalse(fixture.repository.isArticleExists(3867))
            assertEquals(1, task.scanOnce())
            assertEquals(2, attempts)
        }
    }

    @Test
    fun `failed parsing is retried rather than permanently marking article seen`() = runBlocking {
        Fixture().use { fixture ->
            var attempts = 0
            val task = fixture.task {
                if (++attempts == 1) error("temporary parser failure")
                RedemptionCodeResult(code = "ER-3867")
            }
            assertEquals(0, task.scanOnce())
            assertEquals(1, task.scanOnce())
            assertEquals(0, task.scanOnce())
            assertEquals(2, attempts)
        }
    }

    @Test
    fun `failed persistence does not consume the article ID`() = runBlocking {
        Fixture().use { fixture ->
            fixture.repository.failNextInsert = true
            var attempts = 0
            val task = fixture.task {
                attempts++
                RedemptionCodeResult(code = "ER-3867")
            }
            assertEquals(0, task.scanOnce())
            assertEquals(1, task.scanOnce())
            assertEquals(0, task.scanOnce())
            assertEquals(2, attempts)
            assertEquals(3867, fixture.repository.findLatestRedemptionCodes().single().articleId)
        }
    }

    @Test
    fun `missing AI result is not persisted as a completed scan`() = runBlocking {
        Fixture().use { fixture ->
            var attempts = 0
            val task = fixture.task {
                if (++attempts == 1) null else RedemptionCodeResult(code = "ER-3867")
            }
            assertEquals(0, task.scanOnce())
            assertFalse(fixture.repository.isArticleExists(3867))
            assertEquals(1, task.scanOnce())
            assertEquals(2, attempts)
        }
    }

    @Test
    fun `overlapping scans only classify an article once`() = runBlocking {
        Fixture().use { fixture ->
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            var attempts = 0
            val task = fixture.task {
                attempts++
                entered.complete(Unit)
                release.await()
                RedemptionCodeResult(code = "ER-3867")
            }
            val first = async { task.scanOnce() }
            withTimeout(5_000) { entered.await() }
            val second = async { task.scanOnce() }
            yield()
            release.complete(Unit)
            assertEquals(listOf(1, 0), awaitAll(first, second))
            assertEquals(1, attempts)
        }
    }

    @Test
    fun `starting twice does not create duplicate polling jobs`() = runBlocking {
        Fixture().use { fixture ->
            val parent = SupervisorJob()
            val scope = CoroutineScope(coroutineContext + parent)
            val fetched = CompletableDeferred<Unit>()
            val task = fixture.task(
                fetchArticles = { fetched.complete(Unit); emptyList() },
                scope = scope,
            ) { RedemptionCodeResult() }
            try {
                task.start(60_000)
                withTimeout(5_000) { fetched.await() }
                task.start(60_000)
                yield()
                assertEquals(1, parent.children.count(), "start must be idempotent")
            } finally {
                parent.cancelAndJoin()
            }
        }
    }

    @Test
    fun `atomic insert ignores existing article without a check then insert race`() {
        Fixture().use { fixture ->
            val original = EternalReturnNewsRecord(
                articleId = 3867, title = "活动", code = "ORIGINAL",
                thumbnailUrl = "https://example.com/news.png",
                createdAt = LocalDateTime.of(2026, 10, 6, 12, 0), contentText = "正文",
                eventStartTime = LocalDateTime.of(2026, 10, 7, 0, 0),
                eventEndTime = LocalDateTime.of(2026, 10, 14, 23, 59),
                isGameActivity = true, isRedemptionCode = true,
                reward = "奖励", note = "说明", startDate = "2026-10-07", endDate = "2026-10-14",
                processedAt = LocalDateTime.of(2026, 10, 7, 12, 0),
            )
            assertTrue(fixture.repository.insert(original))
            assertFalse(fixture.repository.insert(original.copy(
                title = "新标题", code = "CHANGED", processedAt = original.processedAt.plusDays(1),
            )))
            assertEquals(0, fixture.repository.existenceChecks, "insert must rely on the unique article ID atomically")
            val stored = fixture.repository.findLatestRedemptionCodes().single()
            assertEquals(original.copy(id = stored.id), stored)
        }
    }

    private class Fixture : AutoCloseable {
        private val directory = Files.createTempDirectory("news-article-id-test")
        private val manager = DatabaseManager(DatabaseBackend.SQLITE, directory.resolve("news.db"))
        val repository = TestRepository(manager)

        fun sql(sql: String) = manager.useConnection { connection ->
            connection.createStatement().use { it.execute(sql) }
        }

        fun task(
            fetchArticles: suspend () -> List<EternalReturnNews.Article> = { listOf(article(3867)) },
            scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            classify: suspend () -> RedemptionCodeResult?,
        ) = EternalReturnNewsTask(
            repository, fetchArticles, { "正文" }, { _, _ -> classify() }, scope, contentFetchDelayMillis = 0,
        )

        override fun close() {
            manager.close()
            directory.toFile().deleteRecursively()
        }
    }

    private class TestRepository(manager: DatabaseManager) : NewsRepository(manager) {
        var failNextInsert = false
        var existenceChecks = 0
        override fun isArticleExists(articleId: Int): Boolean {
            existenceChecks++
            return super.isArticleExists(articleId)
        }
        override fun insert(record: EternalReturnNewsRecord): Boolean {
            if (failNextInsert) {
                failNextInsert = false
                return false
            }
            return super.insert(record)
        }
    }

    companion object {
        private fun article(id: Int) = EternalReturnNews.Article(
            id = id, boardId = 1, categoryId = 1, viewCount = 0, isHidden = 0, isPinned = false,
            createdAt = "2026-10-07T12:00:00+09:00", updatedAt = "2026-10-07T12:00:00+09:00",
            i18ns = EternalReturnNews.I18ns(zhCN = EternalReturnNews.LocaleContent(
                locale = "zh_CN", title = "活动", contentLink = "/posts/news/$id",
            )),
        )
    }
}
