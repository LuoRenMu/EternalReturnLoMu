package cn.luorenmu.plugins.news

import cn.luorenmu.repository.entity.EternalReturnNewsRecord
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

internal class RedemptionPushService(
    private val store: RedemptionPushStore,
    private val records: suspend () -> List<EternalReturnNewsRecord>,
    private val groups: () -> List<String>,
    private val permitted: (String) -> Boolean,
    private val send: suspend (CodePush) -> Unit,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val log = KotlinLogging.logger {}
    private val mutex = Mutex()
    suspend fun tick(): Int = mutex.withLock {
        val now = clock.millis()
        val localNow = LocalDateTime.ofInstant(clock.instant(), ZONE)
        val byCode = records().flatMap { record -> codes(record.code).map { it to record } }.groupBy({ it.first }, { it.second })
        for ((code, entries) in byCode) {
            val record = entries.maxBy { it.processedAt }
            val first = entries.minOf { it.processedAt }
            val end = record.eventEndTime ?: record.endDate?.trim()?.take(10)?.let {
                runCatching { LocalDate.parse(it).atTime(LocalTime.MAX) }.getOrNull()
            }
            val start = record.eventStartTime ?: record.startDate?.trim()?.take(10)?.let {
                runCatching { LocalDate.parse(it).atStartOfDay() }.getOrNull()
            }
            if (first.isAfter(localNow) || start?.isAfter(localNow) == true) continue
            val endMillis = end?.atZone(ZONE)?.toInstant()?.toEpochMilli()
            if (endMillis != null && endMillis <= now) {
                store.enqueue(code, listOf(NEW, ENDING), emptyList(), "", now)
                continue
            }
            val fresh = !first.isBefore(localNow.minusDays(1))
            val ending = endMillis != null && endMillis - now <= DAY
            val unseen = !store.hasEvent(code, NEW)
            val targetGroups = groups().filter(permitted)
            val deadline = endMillis ?: now + DAY
            val suffix = end?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))?.let { "\n到期：$it（北京时间）" }.orEmpty()
            if (unseen && fresh && ending) {
                store.enqueue(code, listOf(NEW, ENDING), targetGroups,
                    "发现新兑换码，即将过期：$code$suffix", deadline)
            } else {
                if (unseen) store.enqueue(code, listOf(NEW), if (fresh) targetGroups else emptyList(),
                    "发现新的兑换码：$code$suffix", minOf(deadline, first.atZone(ZONE).toInstant().toEpochMilli() + DAY))
                if (ending) store.enqueue(code, listOf(ENDING), targetGroups,
                    "兑换码即将过期：$code$suffix", deadline)
            }
        }
        var sent = 0
        for (push in store.pending(clock.millis())) {
            currentCoroutineContext().ensureActive()
            if (!store.claim(push, clock.millis())) continue
            if (!permitted(push.group)) {
                store.finish(push, false)
                continue
            }
            try {
                // The sender rechecks permission/deadline after rate-limit waiting.
                withTimeout(60_000) { send(push) }
                store.finish(push, true)
                sent++
            } catch (error: Exception) {
                store.finish(push, false)
                currentCoroutineContext().ensureActive()
                log.warn { "兑换码推送结果不确定，已禁止自动重发：${error.javaClass.simpleName}" }
                // Do not retry an ambiguous external side effect.
            }
        }
        sent
    }

    companion object {
        private val ZONE = ZoneId.of("Asia/Shanghai")
        private const val DAY = 86_400_000L
        private const val NEW = "NEW"
        private const val ENDING = "ENDING"
        internal fun codes(raw: String?): List<String> = raw.orEmpty()
            .split(Regex("[\\s,，、;；|/]+"))
            .map { it.trim('`', ' ', '\"', '\'').uppercase(Locale.ROOT) }
            .filter { it.matches(Regex("[A-Z0-9_-]{3,128}")) }.distinct()
    }
}
