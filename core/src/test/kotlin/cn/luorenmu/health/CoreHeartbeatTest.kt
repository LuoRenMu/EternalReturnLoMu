package cn.luorenmu.health

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CoreHeartbeatTest {
    private val config = HeartbeatConfig(enabled = true, startupGraceSeconds = 0)
    private class TestClock(var time: Instant = Instant.parse("2026-10-04T00:00:00Z")) : Clock() {
        override fun instant(): Instant = time
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        fun advance(seconds: Long) { time = time.plusSeconds(seconds) }
    }

    @Test
    fun healthyChecksNeverSendEmail() = runBlocking {
        val sent = mutableListOf<HealthAlert>()
        val monitor = CoreHeartbeat(config, "QG_BOT", listOf(HealthCheck("qq", "QQ") {
            HealthResult(true)
        }), { sent += it })
        repeat(3) { monitor.checkOnce() }
        assertEquals("healthy", monitor.status().state)
        assertEquals(0, monitor.status().checks.single().consecutiveFailures)
        assertTrue(sent.isEmpty())
    }

    @Test
    fun confirmsFailuresCombinesIssuesAndLimitsRepeatedAlerts() = runBlocking {
        val sent = mutableListOf<HealthAlert>()
        val clock = TestClock()
        val monitor = CoreHeartbeat(config, "QG_BOT", listOf(
            HealthCheck("qq", "QQ") { HealthResult(false, "鉴权失败") },
            HealthCheck("db", "数据库") { HealthResult(false, "超时") },
        ), { sent += it }, clock)
        monitor.checkOnce()
        assertTrue(sent.isEmpty())
        monitor.checkOnce()
        assertEquals(1, sent.size)
        assertEquals(listOf("qq", "db"), sent.single().failures.map { it.id })
        repeat(3) { clock.advance(60); monitor.checkOnce() }
        assertEquals(1, sent.size)
        clock.advance(1800)
        monitor.checkOnce()
        assertEquals(2, sent.size)
        assertEquals(clock.time.toString(), monitor.status().lastAlertAt)
    }

    @Test
    fun transientFailureResetsAndFlappingCannotBypassCooldown() = runBlocking {
        val sent = mutableListOf<HealthAlert>()
        val clock = TestClock()
        var healthy = false
        val monitor = CoreHeartbeat(config, "QQ", listOf(HealthCheck("qq", "QQ") {
            HealthResult(healthy)
        }), { sent += it }, clock)
        monitor.checkOnce()
        healthy = true
        monitor.checkOnce()
        assertEquals(0, monitor.status().checks.single().consecutiveFailures)
        healthy = false
        monitor.checkOnce()
        assertTrue(sent.isEmpty())
        monitor.checkOnce()
        assertEquals(1, sent.size)
        healthy = true
        clock.advance(60)
        monitor.checkOnce()
        healthy = false
        repeat(2) { clock.advance(60); monitor.checkOnce() }
        assertEquals(1, sent.size)
    }

    @Test
    fun failedSmtpIsRetriedButNotEveryScanAndDoesNotCountAsDelivered() = runBlocking {
        val clock = TestClock()
        var attempts = 0
        val monitor = CoreHeartbeat(config.copy(failureThreshold = 1), "QQ",
            listOf(HealthCheck("qq", "QQ") { HealthResult(false) }), {
                attempts++
                if (attempts == 1) throw IllegalStateException("sensitive SMTP password")
            }, clock)
        monitor.checkOnce()
        assertNull(monitor.status().lastAlertAt)
        assertEquals("IllegalStateException", monitor.status().lastNotificationError)
        clock.advance(60)
        monitor.checkOnce()
        assertEquals(1, attempts)
        clock.advance(240)
        monitor.checkOnce()
        assertEquals(2, attempts)
        assertNull(monitor.status().lastNotificationError)
        assertEquals(clock.time.toString(), monitor.status().lastAlertAt)
    }

    @Test
    fun timeoutsAndExceptionsAreIsolatedWithoutLeakingRawMessages() = runBlocking {
        val sent = mutableListOf<HealthAlert>()
        val monitor = CoreHeartbeat(config.copy(timeoutSeconds = 1, failureThreshold = 1), "QQ", listOf(
            HealthCheck("timeout", "超时探针") { delay(5000); HealthResult(true) },
            HealthCheck("error", "错误探针") { error("password=private-token") },
            HealthCheck("ok", "正常探针") { HealthResult(true) },
        ), { sent += it })
        withTimeout(3000) { monitor.checkOnce() }
        assertEquals("检查超时", monitor.status().checks[0].detail)
        assertEquals("检查失败：IllegalStateException", monitor.status().checks[1].detail)
        assertTrue(monitor.status().checks[2].healthy == true)
        assertEquals(2, sent.single().failures.size)
        assertFalse(monitor.status().toString().contains("private-token"))
    }

    @Test
    fun parentCancellationDoesNotBecomeFailureOrEmail() = runBlocking {
        var sent = false
        val entered = CompletableDeferred<Unit>()
        val monitor = CoreHeartbeat(config.copy(failureThreshold = 1), "QQ", listOf(
            HealthCheck("qq", "QQ") { entered.complete(Unit); awaitCancellation() },
        ), { sent = true })
        val scan = async { monitor.checkOnce() }
        entered.await()
        scan.cancelAndJoin()
        assertFalse(sent)
        assertNull(monitor.status().lastCheckedAt)
    }

    @Test
    fun cancelledSdkChildDoesNotKillMonitorOrSkipOtherProbes() = runBlocking {
        var sent = 0
        val monitor = CoreHeartbeat(config.copy(failureThreshold = 1), "QQ", listOf(
            HealthCheck("qq", "QQ") { throw CancellationException("SDK child cancelled") },
            HealthCheck("db", "数据库") { HealthResult(true) },
        ), { sent++ })
        monitor.checkOnce()
        assertEquals("检查被中断", monitor.status().checks.first().detail)
        assertTrue(monitor.status().checks.last().healthy == true)
        assertEquals(1, sent)
        monitor.checkOnce()
        assertEquals(2, monitor.status().checks.first().consecutiveFailures)
        assertEquals(1, sent)
    }

    @Test
    fun concurrentScansDoNotOverlap() = runBlocking {
        val active = AtomicInteger()
        val maximum = AtomicInteger()
        val monitor = CoreHeartbeat(config, "QQ", listOf(HealthCheck("qq", "QQ") {
            val count = active.incrementAndGet()
            maximum.updateAndGet { maxOf(it, count) }
            delay(20)
            active.decrementAndGet()
            HealthResult(true)
        }), {})
        val scans = List(4) { async(Dispatchers.Default) { monitor.checkOnce() } }
        scans.forEach { it.await() }
        assertEquals(1, maximum.get())
    }

    @Test
    fun startsOnlyOnceAndCancelsOnShutdownIncludingGracePeriod() = runBlocking {
        var calls = 0
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val monitor = CoreHeartbeat(config.copy(startupGraceSeconds = 60), "QQ",
                listOf(HealthCheck("qq", "QQ") { calls++; HealthResult(true) }), {})
            val job = monitor.start(scope)!!
            assertSame(job, monitor.start(scope))
            delay(20)
            monitor.stop()
            withTimeout(1000) { job.join() }
            assertEquals("stopped", monitor.status().state)
            assertEquals(0, calls)
        } finally { scope.cancel() }
    }

    @Test
    fun backgroundLoopScansAndSendsConfirmedFailures() = runBlocking {
        val notified = CompletableDeferred<HealthAlert>()
        val monitor = CoreHeartbeat(config.copy(intervalSeconds = 1), "QQ",
            listOf(HealthCheck("qq", "QQ") { HealthResult(false, "鉴权失败") }), { notified.complete(it) })
        val job = monitor.start(this)!!
        try {
            val alert = withTimeout(3000) { notified.await() }
            assertEquals(2, alert.failures.single().consecutiveFailures)
        } finally {
            monitor.stop()
            withTimeout(1000) { job.join() }
        }
        assertEquals("stopped", monitor.status().state)
    }

    @Test
    fun disabledConfigurationDoesNotProbeOrStart() = runBlocking {
        var calls = 0
        val monitor = CoreHeartbeat(config.copy(enabled = false), "QQ",
            listOf(HealthCheck("qq", "QQ") { calls++; HealthResult(false) }), { error("must not send") })
        assertNull(monitor.start(this))
        monitor.checkOnce()
        assertEquals(0, calls)
        assertEquals("disabled", monitor.status().state)
    }
}
