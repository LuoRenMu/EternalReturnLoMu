package cn.luorenmu.service

import cn.luorenmu.Adapter
import cn.luorenmu.SERVER_PORT
import cn.luorenmu.common.util.DatabaseBackend
import cn.luorenmu.common.util.DatabaseManager
import cn.luorenmu.currentAdapter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 *
 * @author LoMu
 * Date 2026/8/16 15:30
 */
class AdminSystemServiceTest {
    @Test
    fun reportsRuntimeAndMachineSummaryWithoutInitializingDatabase() {
        currentAdapter = Adapter.ONE_BOT
        SERVER_PORT = 5752

        val view = AdminSystemService(DatabaseManager(DatabaseBackend.SQLITE)).view()

        assertEquals("ONE_BOT", view.adapter)
        assertEquals("SQLite", view.databaseBackend)
        assertEquals(5752, view.runtimePort)
        assertTrue(view.uptimeMillis >= 0)
        assertTrue(view.processors > 0)
        assertTrue(view.jvmMemoryUsedBytes >= 0)
        assertTrue(view.jvmMemoryMaxBytes > 0)
        assertTrue(view.startedAt.isNotBlank())
        assertTrue(view.serverTime.isNotBlank())
    }
}
