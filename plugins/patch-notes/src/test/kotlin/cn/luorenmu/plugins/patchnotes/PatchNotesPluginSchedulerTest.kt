package cn.luorenmu.plugins.patchnotes

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals

class PatchNotesPluginSchedulerTest {
    @Test fun `enabling twice scans once and disabling cancels pending startup`() = runBlocking {
        val scanned = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        val service = PatchNotesService(Files.createTempDirectory("patch-scheduler"), {
            calls.incrementAndGet(); scanned.complete(Unit); emptyList()
        }, { error("unused") }, { _, _ -> error("unused") })
        val plugin = PatchNotesPlugin({ service }, 0)
        try {
            plugin.onEnable()
            plugin.onEnable()
            withTimeout(2_000) { scanned.await() }
            delay(50)
            assertEquals(1, calls.get())
        } finally {
            plugin.onDisable()
        }
        val stopped = PatchNotesPlugin({ calls.incrementAndGet(); service }, 100)
        stopped.onEnable()
        stopped.onDisable()
        delay(150)
        assertEquals(1, calls.get())
    }
}
