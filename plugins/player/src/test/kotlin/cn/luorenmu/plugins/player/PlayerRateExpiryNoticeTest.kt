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
