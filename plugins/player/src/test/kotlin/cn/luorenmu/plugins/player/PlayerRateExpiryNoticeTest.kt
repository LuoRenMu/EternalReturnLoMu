package cn.luorenmu.plugins.player

import cn.luorenmu.repository.entity.EternalReturnNewsRecord
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 *
 * @author LoMu
 * Date 2026/8/16 15:30
 */
class PlayerRateExpiryNoticeTest {
    private val today = LocalDate.of(2026, 8, 16)

    @Test
    fun `appends warning when a redemption code expires tomorrow`() {
        val result = appendRedemptionCodeExpiryNotice(
            rate = "一般般啦",
            records = listOf(redemptionCode("2026-08-17")),
            today = today,
        )

        assertEquals("一般般啦（兑换码即将过期：ER-WEEKEND）", result)
    }

    @Test
    fun `keeps original rate when no redemption code expires tomorrow`() {
        val records = listOf(
            redemptionCode("2026-08-16"),
            redemptionCode("2026-08-18"),
            redemptionCode("2026-08-17", isRedemptionCode = false),
        )

        assertEquals("一般般啦", appendRedemptionCodeExpiryNotice("一般般啦", records, today))
    }

    @Test
    fun `shows every distinct code expiring tomorrow`() {
        val records = listOf(
            redemptionCode("2026-08-17", code = "ER-WEEKEND"),
            redemptionCode("2026-08-17", code = " ER-WEEKEND "),
            redemptionCode("2026-08-17", code = "ER-GIFT"),
            redemptionCode("2026-08-17", code = null),
        )

        assertEquals(
            "较为差劲（兑换码即将过期：ER-WEEKEND、ER-GIFT）",
            appendRedemptionCodeExpiryNotice("较为差劲", records, today),
        )
    }

    private fun redemptionCode(
        endDate: String,
        isRedemptionCode: Boolean = true,
        code: String? = "ER-WEEKEND",
    ) = EternalReturnNewsRecord(
        articleId = 1,
        title = "周末兑换码",
        isRedemptionCode = isRedemptionCode,
        code = code,
        endDate = endDate,
    )
}
