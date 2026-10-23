package cn.luorenmu.plugins.qqmedia

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QQInteractionGuardTest {
    @Test fun `callback deduplication expires stale IDs`() {
        var now = 0L
        val guard = QQInteractionGuard(ttlMillis = 100, nowMillis = { now })
        assertTrue(guard.shouldProcess("event"))
        now = 99
        assertFalse(guard.shouldProcess("event"))
        now = 100
        assertTrue(guard.shouldProcess("event"))
    }

    @Test fun `callback deduplication cache stays bounded`() {
        val guard = QQInteractionGuard(maxEntries = 2)
        assertTrue(guard.shouldProcess("a"))
        assertTrue(guard.shouldProcess("b"))
        assertTrue(guard.shouldProcess("c"))
        assertFalse(guard.shouldProcess("b"))
        assertFalse(guard.shouldProcess("c"))
        assertTrue(guard.shouldProcess("a"))
    }
}
