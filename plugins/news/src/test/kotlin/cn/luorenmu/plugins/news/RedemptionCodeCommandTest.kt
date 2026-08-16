package cn.luorenmu.plugins.news

import cn.luorenmu.common.annotation.BotCommand
import cn.luorenmu.repository.entity.EternalReturnNewsRecord
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * RedemptionCodeCommand 元数据测试（自包含，无需 Koin/网络/数据库）。
  *
  * @author LoMu
  * Date 2026/8/16 15:30
 */
class RedemptionCodeCommandTest {

    private val cmd = RedemptionCodeCommand()

    @Test
    fun annotationIsCorrect() {
        val anno = cmd::class.java.getAnnotation(BotCommand::class.java)
        assertNotNull(anno)
        assertEquals("兑换码", anno.alias)
        assertEquals("兑换码", anno.name)
        assertEquals("<limit>", anno.value)
    }

    @Test
    fun optionalsAreComplete() {
        assertEquals(1, cmd.optionals.size)
        assertEquals("limit", cmd.optionals[0].name)
        assertFalse(cmd.optionals[0].required)
        assertEquals("/兑换码", cmd.example)
    }

    @Test
    fun descriptionIsComplete() {
        assertTrue(cmd.description.contains("兑换码"))
    }

    @Test
    fun expiringTomorrowAddsWarningAfterCode() {
        val today = LocalDate.of(2026, 8, 16)
        val record = redemptionCode(endDate = "2026-08-17")

        assertEquals("兑换码: `ER-WEEKEND`（兑换码即将过期）", record.displayCodeLine(today))
    }

    @Test
    fun otherExpirationDatesDoNotAddWarning() {
        val today = LocalDate.of(2026, 8, 16)

        assertEquals("兑换码: `ER-WEEKEND`", redemptionCode(endDate = "2026-08-16").displayCodeLine(today))
        assertEquals("兑换码: `ER-WEEKEND`", redemptionCode(endDate = "2026-08-18").displayCodeLine(today))
        assertEquals(
            "兑换码: `ER-WEEKEND`",
            redemptionCode(endDate = "2026-08-17", isRedemptionCode = false).displayCodeLine(today),
        )
    }

    private fun redemptionCode(
        endDate: String,
        isRedemptionCode: Boolean = true,
    ) = EternalReturnNewsRecord(
        articleId = 1,
        title = "周末兑换码",
        isRedemptionCode = isRedemptionCode,
        code = "ER-WEEKEND",
        endDate = endDate,
    )
}
