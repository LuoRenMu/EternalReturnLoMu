package cn.luorenmu.plugins.player

import cn.luorenmu.repository.entity.EternalReturnNewsRecord
import love.forte.simbot.message.Messages
import love.forte.simbot.message.PlainText
import love.forte.simbot.message.toText
import java.time.LocalDateTime
import kotlin.test.*

class PlayerPlainReplyRegressionTest {
    private val now = LocalDateTime.of(2026, 10, 1, 12, 0)
    private val image = "image fixture".toText()
    private fun record(code: String? = "CODE", end: LocalDateTime? = now.plusDays(3)) =
        EternalReturnNewsRecord(articleId = 1, title = "兑换码", code = code,
            createdAt = now.minusDays(3), processedAt = now.minusDays(3),
            eventStartTime = now.minusDays(3), eventEndTime = end)

    private fun caption(records: List<EternalReturnNewsRecord>, at: LocalDateTime = now): String {
        val elements = assertIs<Messages>(playerReplyWithRedemptionCodes(image, records, at)).toList()
        assertEquals(2, elements.size)
        assertSame(image, elements.first())
        return assertIs<PlainText>(elements.last()).text
    }

    @Test fun `new codes appear on their first discovery calendar day`() {
        assertEquals("发现新的兑换码:NEW", caption(listOf(record("NEW").copy(processedAt = now.minusHours(11)))))
        assertSame(image, playerReplyWithRedemptionCodes(image,
            listOf(record().copy(processedAt = now.minusHours(13))), now))
    }

    @Test fun `only valid codes ending today are expiring not tomorrow`() {
        assertEquals("即将过期的兑换码:TODAY", caption(listOf(
            record("TODAY", now.plusHours(1)), record("TOMORROW", now.plusDays(1)))))
        assertSame(image, playerReplyWithRedemptionCodes(image, listOf(record(end = now.plusDays(1))), now))
    }

    @Test fun `new and expiring strings accompany the image and codes are deduplicated`() {
        assertEquals("发现新的兑换码:NEW\n即将过期的兑换码:END,BOTH", caption(listOf(
            record(" NEW ").copy(processedAt = now), record("NEW").copy(processedAt = now),
            record("END", now.plusHours(1)), record(" END ", now.plusHours(2)),
            record("BOTH", now.plusHours(1)).copy(processedAt = now),
            record("BOTH").copy(processedAt = now))))
    }

    @Test fun `expired inactive blank and future discoveries do not append text`() {
        val records = listOf(record(end = now.minusSeconds(1)).copy(processedAt = now),
            record().copy(eventStartTime = now.plusHours(1), processedAt = now),
            record(" ").copy(processedAt = now), record(null).copy(processedAt = now),
            record().copy(processedAt = now.plusSeconds(1)), record())
        assertSame(image, playerReplyWithRedemptionCodes(image, records, now))
        assertSame(image, playerReplyWithRedemptionCodes(image, emptyList(), now))
    }

    @Test fun `discovery is first persistence not article creation`() {
        assertEquals("发现新的兑换码:NEW", caption(listOf(record("NEW")
            .copy(createdAt = now.minusMonths(1), processedAt = now))))
        assertSame(image, playerReplyWithRedemptionCodes(image,
            listOf(record().copy(createdAt = now, processedAt = now.minusDays(1))), now))
    }

    @Test fun `legacy date strings and exact deadline are supported`() {
        assertEquals("即将过期的兑换码:LEGACY,NOW", caption(listOf(
            record("LEGACY", null).copy(endDate = "2026-10-01"), record("NOW", now))))
        assertSame(image, playerReplyWithRedemptionCodes(image,
            listOf(record("LEGACY", null).copy(endDate = "2026-09-30")), now))
    }

    @Test fun `new code notice stops at midnight even within 24 hours`() {
        val discovered = record("NEW").copy(processedAt = now.toLocalDate().atTime(23, 59))
        assertEquals("发现新的兑换码:NEW", caption(listOf(discovered), discovered.processedAt))
        assertSame(image, playerReplyWithRedemptionCodes(image, listOf(discovered),
            now.toLocalDate().plusDays(1).atStartOfDay()))
    }
}
