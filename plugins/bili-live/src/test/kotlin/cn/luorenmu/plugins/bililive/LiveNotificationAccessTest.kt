package cn.luorenmu.plugins.bililive

import cn.luorenmu.plugins.qqmedia.QQGroupPermissionResult
import cn.luorenmu.plugins.qqmedia.QQGroupPermissionStatus
import kotlinx.coroutines.*
import kotlin.test.*

class LiveNotificationAccessTest {
    @Test fun `permission must explicitly be enabled and prior allows are never trusted`() = runBlocking<Unit> {
        var status = QQGroupPermissionStatus.ENABLED
        var clock = 0L
        var calls = 0
        val waits = mutableListOf<Long>()
        val access = LiveNotificationAccess({ calls++; QQGroupPermissionResult(status) },
            { waits += it; clock += it * 1_000_000 }, { clock })
        assertTrue(access.enabled("group"))
        for (denied in QQGroupPermissionStatus.entries.filter { it != QQGroupPermissionStatus.ENABLED }) {
            status = denied
            assertFalse(access.enabled("group"))
        }
        assertEquals(6, calls)
        assertEquals(listOf(2100L, 2100L, 2100L, 2100L, 2100L), waits)
    }

    @Test fun `network failures fail closed but unload cancellation is not swallowed`() = runBlocking<Unit> {
        assertFalse(LiveNotificationAccess({ error("offline") }).enabled("group"))
        val cancelled = LiveNotificationAccess({ throw CancellationException("unload") })
        assertFailsWith<CancellationException> { cancelled.enabled("group") }
    }
}
