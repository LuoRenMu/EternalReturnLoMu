package cn.luorenmu.plugins.qqmedia

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.test.*

class QQGroupPushPermissionsTest {
    @TempDir lateinit var root: Path
    private fun store() = QQGroupPushPermissions(root.resolve("permissions.db"))

    @Test fun `stale events never override newer state and close wins timestamp ties across instances`() {
        val first = store()
        val second = store()
        assertFalse(first.canPush("group"))
        assertEquals(1, first.updatePermission("group", true, 100))
        assertEquals(0, second.updatePermission("group", false, 99))
        assertTrue(second.canPush("group"))
        second.updatePermission("group", false, 100)
        assertEquals(0, first.updatePermission("group", true, 100))
        assertFalse(first.canPush("group"))
        second.updatePermission("group", true, 101)
        assertEquals(listOf("group"), first.pushGroups())
    }

    @Test fun `concurrent equal timestamp events cannot reopen a closed group`() {
        store().updatePermission("group", true, 1)
        val executor = Executors.newFixedThreadPool(4)
        try {
            executor.invokeAll(List(16) { index -> Callable { store().updatePermission("group", index % 2 == 0, 2) } }).forEach { it.get() }
        } finally { executor.shutdownNow() }
        assertFalse(store().canPush("group"))
    }
}
