package cn.luorenmu.service

import cn.luorenmu.repository.entity.EternalReturnNewsRecord
import java.time.LocalDate

/**
 * 活动展示规则。
  *
  * @author LoMu
  * Date 2026/8/16 15:30
 */
object GameActivityVisibility {
    fun EternalReturnNewsRecord.isVisibleOn(today: LocalDate): Boolean {
        val start = startDate.toLocalDateOrNull()
        val end = endDate.toLocalDateOrNull()
        if (start != null && start.isAfter(today)) return false
        return end == null || !end.isBefore(today.minusDays(1))
    }

    fun EternalReturnNewsRecord.displayStatus(today: LocalDate): String {
        val end = endDate.toLocalDateOrNull() ?: return "有效期未注明"
        return if (end == today.minusDays(1)) "已过期 1 天" else "有效中"
    }

    fun String?.toLocalDateOrNull(): LocalDate? {
        val value = this?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return runCatching { LocalDate.parse(value.take(10)) }.getOrNull()
    }
}
