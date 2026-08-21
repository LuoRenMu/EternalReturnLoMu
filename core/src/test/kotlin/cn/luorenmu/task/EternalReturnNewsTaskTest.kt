package cn.luorenmu.task

import cn.luorenmu.ai.AIConfig
import cn.luorenmu.ai.KoogLLMClient
import cn.luorenmu.ai.news.NewsClassifier
import cn.luorenmu.common.util.DatabaseBackend
import cn.luorenmu.common.util.DatabaseManager
import cn.luorenmu.repository.NewsRepository
import java.nio.file.Path
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EternalReturnNewsTaskTest {
    private val task = EternalReturnNewsTask(
        NewsClassifier(KoogLLMClient(AIConfig())),
        NewsRepository(DatabaseManager(DatabaseBackend.SQLITE, Path.of("build", "unused-news-test.db"))),
    )
    private val now = LocalDateTime.of(2026, 8, 21, 16, 45)

    @Test
    fun `missing activity dates default to today and two weeks later at midnight`() = with(task) {
        val record = parsed(
            NewsClassifier.RedemptionCodeResult(isGameActivity = true)
        ).toRecord(now)

        assertEquals(LocalDateTime.of(2026, 8, 21, 0, 0), record.eventStartTime)
        assertEquals(LocalDateTime.of(2026, 9, 4, 0, 0), record.eventEndTime)
        assertEquals("2026-08-21", record.startDate)
        assertEquals("2026-09-04", record.endDate)
    }

    @Test
    fun `explicit hours and minutes are persisted and invalid values fall back to zero`() = with(task) {
        val record = parsed(
            NewsClassifier.RedemptionCodeResult(
                isGameActivity = true,
                startDate = "2026-08-22",
                startHours = 9,
                startMinutes = 30,
                endDate = "2026-08-24",
                endHours = 99,
                endMinutes = -1,
            )
        ).toRecord(now)

        assertEquals(LocalDateTime.of(2026, 8, 22, 9, 30), record.eventStartTime)
        assertEquals(LocalDateTime.of(2026, 8, 24, 0, 0), record.eventEndTime)
    }

    @Test
    fun `code presence alone determines redemption code compatibility flag`() = with(task) {
        val withoutCode = parsed(NewsClassifier.RedemptionCodeResult(isGameActivity = true, code = "详见活动页面")).toRecord(now)
        val withCode = parsed(NewsClassifier.RedemptionCodeResult(code = "  ER-2026  ")).toRecord(now)

        assertFalse(withoutCode.isRedemptionCode)
        assertNull(withoutCode.code)
        assertTrue(withCode.isRedemptionCode)
        assertEquals("ER-2026", withCode.code)
        assertTrue(withCode.isGameActivity)
    }

    private fun parsed(result: NewsClassifier.RedemptionCodeResult) = EternalReturnNewsTask.ParsedNews(
        articleId = 1,
        title = "活动",
        createdAt = now,
        contentText = "正文",
        redemptionCode = result,
    )
}
