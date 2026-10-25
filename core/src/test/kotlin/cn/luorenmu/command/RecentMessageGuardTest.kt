package cn.luorenmu.command

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecentMessageGuardTest {
    @Test
    fun `same incoming message can be replied to only once`() {
        var now = 1_000L
        val guard = RecentMessageGuard(ttlMillis = 300_000, maxEntries = 2, nowMillis = { now })

        assertTrue(guard.shouldProcess("message-a"))
        assertFalse(guard.shouldProcess("message-a"))
        assertTrue(guard.shouldProcess("message-b"))
        assertFalse(guard.shouldProcess("message-b"))
        assertTrue(guard.shouldProcess(""))
        now += 300_000
        assertTrue(guard.shouldProcess("message-a"))
    }

    @Test
    fun `guard bounds retained ids and handles concurrent deliveries`() {
        val guard = RecentMessageGuard(ttlMillis = 300_000, maxEntries = 2, nowMillis = { 1_000L })
        assertTrue(guard.shouldProcess("a"))
        assertTrue(guard.shouldProcess("b"))
        assertTrue(guard.shouldProcess("c"))
        assertTrue(guard.shouldProcess("a"), "oldest id was evicted")

        val concurrent = RecentMessageGuard()
        val accepted = java.util.concurrent.atomic.AtomicInteger()
        val workers = (1..20).map {
            Thread { if (concurrent.shouldProcess("same")) accepted.incrementAndGet() }
        }
        workers.forEach(Thread::start)
        workers.forEach(Thread::join)
        assertTrue(accepted.get() == 1)
    }
}
