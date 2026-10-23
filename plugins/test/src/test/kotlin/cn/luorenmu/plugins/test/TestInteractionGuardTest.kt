package cn.luorenmu.plugins.test

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestInteractionGuardTest {
    @Test fun `deduplication expires without retaining stale event IDs forever`() {
        var now = 0L
        val guard = TestInteractionGuard(ttlMillis = 100, nowMillis = { now })
        assertTrue(guard.shouldProcess("event"))
        now = 99
        assertFalse(guard.shouldProcess("event"))
        now = 100
        assertTrue(guard.shouldProcess("event"))
    }

    @Test fun `deduplication memory is bounded and retains recent IDs`() {
        val guard = TestInteractionGuard(maxEntries = 2)
        assertTrue(guard.shouldProcess("a"))
        assertTrue(guard.shouldProcess("b"))
        assertTrue(guard.shouldProcess("c"))
        assertFalse(guard.shouldProcess("b"))
        assertFalse(guard.shouldProcess("c"))
        assertTrue(guard.shouldProcess("a"))
    }
}
