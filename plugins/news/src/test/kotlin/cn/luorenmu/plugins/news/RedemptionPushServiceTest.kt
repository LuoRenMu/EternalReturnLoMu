package cn.luorenmu.plugins.news

import cn.luorenmu.repository.entity.EternalReturnNewsRecord
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.*
import kotlin.test.*

class RedemptionPushServiceTest {
    @TempDir lateinit var root: Path
    private val clock = PushTestClock()
    private fun now() = LocalDateTime.ofInstant(clock.instant(), ZoneId.of("Asia/Shanghai"))
    private fun store() = RedemptionPushStore(root.resolve("push.db"))
    private fun record(code: String = "ABC123", days: Long = 3) = EternalReturnNewsRecord(
        articleId = 1, title = "活动", code = code, processedAt = now(),
        eventStartTime = now().minusDays(1), eventEndTime = now().plusDays(days), isRedemptionCode = true)

    @Test fun `expiry supersedes pending new and revoked queue cannot block other groups`() = runBlocking {
        val ledger = store()
        ledger.enqueue("ABC123", listOf("NEW"), listOf("A"), "new", clock.millis() + 1000)
        ledger.enqueue("ABC123", listOf("ENDING"), listOf("A"), "ending", clock.millis() + 1000)
        assertEquals("ENDING", ledger.pending(clock.millis()).single().phase)
        val service = RedemptionPushService(ledger, { emptyList() }, { emptyList() }, { false },
            { error("revoked group must not send") }, clock)
        assertEquals(0, service.tick())
        assertTrue(ledger.pending(clock.millis()).isEmpty())
    }

    @Test fun `code dedup survives new articles restart and sends only two phases per group`() = runBlocking {
        var records = listOf(record(), record(" abc123 ").copy(articleId = 2))
        val sent = mutableListOf<CodePush>()
        fun service() = RedemptionPushService(store(), { records }, { listOf("A", "B") }, { true }, { sent += it }, clock)
        assertEquals(2, service().tick())
        assertEquals(0, service().tick())
        clock.advance(Duration.ofDays(1))
        records = records + record("ABC123", 2).copy(articleId = 3)
        assertEquals(0, service().tick())
        clock.advance(Duration.ofDays(1))
        assertEquals(2, service().tick())
        assertEquals(0, service().tick())
        assertEquals(4, sent.size)
        assertEquals(4, sent.map { Triple(it.code, it.phase, it.group) }.distinct().size)
        clock.advance(Duration.ofDays(2))
        assertEquals(0, service().tick())
    }

    @Test fun `new expiring code combines phases and never repeats`() = runBlocking {
        val record = record().copy(eventEndTime = now().plusHours(2))
        val sent = mutableListOf<CodePush>()
        val service = RedemptionPushService(store(), { listOf(record) }, { listOf("A") }, { true }, { sent += it }, clock)
        assertEquals(1, service.tick())
        assertTrue(sent.single().body.contains("发现新兑换码，即将过期"))
        assertTrue(store().hasEvent("ABC123", "NEW"))
        assertTrue(store().hasEvent("ABC123", "ENDING"))
        assertEquals(0, service.tick())
    }

    @Test fun `unknown and disabled groups receive nothing and old codes are not new`() = runBlocking {
        val old = record().copy(processedAt = now().minusDays(2))
        val sent = mutableListOf<CodePush>()
        var allowed = false
        val service = RedemptionPushService(store(), { listOf(old) }, { listOf("A") }, { allowed }, { sent += it }, clock)
        assertEquals(0, service.tick())
        allowed = true
        assertEquals(0, service.tick())
        clock.advance(Duration.ofDays(2))
        assertEquals(1, service.tick())
        assertEquals("ENDING", sent.single().phase)
    }

    @Test fun `uncertain result is not retried after restart while other groups continue`() = runBlocking {
        val record = record()
        var attempts = 0
        fun service() = RedemptionPushService(store(), { listOf(record) }, { listOf("A", "B") }, { true }, {
            attempts++
            if (it.group == "A") error("response lost after acceptance")
        }, clock)
        assertEquals(1, service().tick())
        assertEquals(2, attempts)
        assertEquals(0, service().tick())
        assertEquals(2, attempts)
    }

    @Test fun `persistent claims exclude concurrent workers and expire pending delivery`() {
        val first = store()
        val second = store()
        first.enqueue("ABC123", listOf("NEW"), listOf("A"), "text", clock.millis() + 1000)
        second.enqueue("ABC123", listOf("NEW"), listOf("A"), "text", clock.millis() + 1000)
        val push = first.pending(clock.millis()).single()
        assertTrue(first.claim(push, clock.millis()))
        assertFalse(second.claim(push, clock.millis()))
        assertTrue(second.pending(clock.millis()).isEmpty())
        first.enqueue("OTHER", listOf("NEW"), listOf("A"), "text", clock.millis() + 1000)
        clock.advance(Duration.ofSeconds(1))
        assertTrue(second.pending(clock.millis()).isEmpty())
    }

    @Test fun `missing expiry does not invent expiry notification and multiple codes normalize`() = runBlocking {
        assertEquals(listOf("ABC123", "DEF456"), RedemptionPushService.codes("`abc123`，ABC123; DEF456"))
        val record = record().copy(eventEndTime = null)
        val sent = mutableListOf<CodePush>()
        val service = RedemptionPushService(store(), { listOf(record) }, { listOf("A") }, { true }, { sent += it }, clock)
        assertEquals(1, service.tick())
        clock.advance(Duration.ofDays(14))
        assertEquals(0, service.tick())
        assertEquals("NEW", sent.single().phase)
    }
}

private class PushTestClock(private var time: Instant = Instant.parse("2026-10-22T06:00:00Z")) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = this
    override fun instant(): Instant = time
    fun advance(duration: Duration) { time = time.plus(duration) }
}
