package cn.luorenmu.service

import cn.luorenmu.repository.entity.EternalReturnNewsRecord
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 活动展示规则。
  *
  * @author LoMu
  * Date 2026/8/16 15:30
 */
object GameActivityVisibility {
    fun EternalReturnNewsRecord.isGameActivityAt(now: LocalDateTime): Boolean {
        val start = effectiveStartTime() ?: return false
        val end = effectiveEndTime() ?: return false
        return !start.isAfter(now) && !end.isBefore(now)
    }

    fun EternalReturnNewsRecord.hasRedemptionCode(): Boolean = !code.isNullOrBlank()

    fun EternalReturnNewsRecord.isVisibleAt(now: LocalDateTime): Boolean {
        val start = effectiveStartTime() ?: return false
        val end = effectiveEndTime() ?: return false
        return !start.isAfter(now) && !end.isBefore(now.minusDays(1))
    }

    fun EternalReturnNewsRecord.isVisibleOn(today: LocalDate): Boolean {
        return isVisibleAt(today.atTime(12, 0))
    }

    fun EternalReturnNewsRecord.displayStatus(today: LocalDate): String {
        return displayStatus(today.atTime(12, 0))
    }

    fun EternalReturnNewsRecord.displayStatus(now: LocalDateTime): String {
        val end = effectiveEndTime() ?: return "时间无效"
        return if (end.isBefore(now)) "刚刚过期" else "有效中"
    }

    fun EternalReturnNewsRecord.isRedemptionCodeExpiringTomorrow(today: LocalDate): Boolean {
        return hasRedemptionCode() && effectiveEndTime()?.toLocalDate() == today.plusDays(1)
    }

    fun EternalReturnNewsRecord.displayPeriod(): String {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        val start = effectiveStartTime()?.format(formatter) ?: "未注明"
        val end = effectiveEndTime()?.format(formatter) ?: "未注明"
        return "$start 至 $end"
    }

    fun EternalReturnNewsRecord.effectiveStartTime(): LocalDateTime? {
        return eventStartTime
            ?: startDate.toLocalDateOrNull()?.atStartOfDay()
            ?: processedAt.takeIf { hasLegacyActivityEvidence() }?.toLocalDate()?.atStartOfDay()
    }

    fun EternalReturnNewsRecord.effectiveEndTime(): LocalDateTime? {
        return eventEndTime
            ?: endDate.toLocalDateOrNull()?.atTime(LocalTime.MAX)
            ?: effectiveStartTime()?.takeIf { hasLegacyActivityEvidence() }?.plusWeeks(2)
    }

    private fun EternalReturnNewsRecord.hasLegacyActivityEvidence(): Boolean =
        isGameActivity || hasRedemptionCode()

    fun String?.toLocalDateOrNull(): LocalDate? {
        val value = this?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return runCatching { LocalDate.parse(value.take(10)) }.getOrNull()
    }
}


