package cn.luorenmu.plugins.player

import cn.luorenmu.repository.entity.EternalReturnNewsRecord
import cn.luorenmu.service.GameActivityVisibility.effectiveEndTime
import cn.luorenmu.service.GameActivityVisibility.effectiveStartTime
import love.forte.simbot.message.Message
import love.forte.simbot.message.plus
import love.forte.simbot.message.toText
import java.time.LocalDateTime

/** Both adapters keep the original image; notices use calendar days rather than rolling 24-hour windows. */
internal fun playerReplyWithRedemptionCodes(
    image: Message.Element,
    records: List<EternalReturnNewsRecord>,
    now: LocalDateTime,
): Message {
    val today = now.toLocalDate()
    val newlyDiscovered = linkedSetOf<String>()
    val expiring = linkedSetOf<String>()
    records.forEach { record ->
        val code = record.code?.trim()?.takeIf(String::isNotEmpty) ?: return@forEach
        val end = record.effectiveEndTime()
        val start = record.effectiveStartTime()
        if (end == null || end.isBefore(now) || start?.isAfter(now) == true || record.processedAt.isAfter(now)) {
            return@forEach
        }
        if (record.processedAt.toLocalDate() == today) newlyDiscovered.add(code)
        if (end.toLocalDate() == today) expiring.add(code)
    }
    // A newly discovered code that also ends today is shown once, with the more urgent expiry notice.
    newlyDiscovered.removeAll(expiring)
    val notice = buildList {
        if (newlyDiscovered.isNotEmpty()) add("发现新的兑换码:${newlyDiscovered.joinToString(",")}")
        if (expiring.isNotEmpty()) add("即将过期的兑换码:${expiring.joinToString(",")}")
    }.joinToString("\n")
    return if (notice.isEmpty()) image else image.plus(notice.toText())
}
