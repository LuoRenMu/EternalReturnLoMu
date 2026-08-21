package cn.luorenmu.service

import cn.luorenmu.repository.entity.EternalReturnNewsRecord
import cn.luorenmu.service.GameActivityVisibility.isGameActivityAt
import cn.luorenmu.service.GameActivityVisibility.isRedemptionCodeExpiringTomorrow
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GameActivityVisibilityTest {
    private val now = LocalDateTime.of(2026, 8, 21, 12, 30)

    @Test
    fun `game activity state is derived from event time instead of stored flag`() {
        val active = news(
            eventStartTime = now.minusHours(1),
            eventEndTime = now.plusHours(1),
            isGameActivity = false,
        )
        val future = news(
            eventStartTime = now.plusMinutes(1),
            eventEndTime = now.plusDays(1),
            isGameActivity = true,
        )

        assertTrue(active.isGameActivityAt(now))
        assertFalse(future.isGameActivityAt(now))
    }

    @Test
    fun `redemption code state is derived from code instead of stored flag`() {
        val tomorrow = now.toLocalDate().plusDays(1)

        assertTrue(
            news(code = "ER-2026", endDate = tomorrow.toString(), isRedemptionCode = false)
                .isRedemptionCodeExpiringTomorrow(now.toLocalDate())
        )
        assertFalse(
            news(code = "  ", endDate = tomorrow.toString(), isRedemptionCode = true)
                .isRedemptionCodeExpiringTomorrow(now.toLocalDate())
        )
    }

    @Test
    fun `legacy activity without dates receives a two week compatibility window`() {
        val importedAt = LocalDateTime.of(2026, 8, 21, 18, 0)
        val legacy = EternalReturnNewsRecord(
            articleId = 2,
            title = "旧版活动",
            isGameActivity = true,
            processedAt = importedAt,
        )

        assertTrue(legacy.isGameActivityAt(LocalDateTime.of(2026, 8, 22, 0, 0)))
        assertFalse(legacy.isGameActivityAt(LocalDateTime.of(2026, 9, 5, 0, 1)))
    }

    private fun news(
        eventStartTime: LocalDateTime? = null,
        eventEndTime: LocalDateTime? = null,
        isGameActivity: Boolean = false,
        isRedemptionCode: Boolean = false,
        code: String? = null,
        endDate: String? = null,
    ) = EternalReturnNewsRecord(
        articleId = 1,
        title = "活动",
        eventStartTime = eventStartTime,
        eventEndTime = eventEndTime,
        isGameActivity = isGameActivity,
        isRedemptionCode = isRedemptionCode,
        code = code,
        endDate = endDate,
    )
}
