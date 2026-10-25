package cn.luorenmu.plugins.qqmedia

import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

class BackgroundPollsTest {
    @Test fun `player and live polls retain disjoint wall clock slots after slow ticks and reloads`() {
        assertEquals(5_000L, BackgroundPolls.nextDelay(BackgroundPolls.Kind.PLAYER, 0))
        assertEquals(20_000L, BackgroundPolls.nextDelay(BackgroundPolls.Kind.LIVE, 0))
        assertEquals(30_000L, BackgroundPolls.nextDelay(BackgroundPolls.Kind.PLAYER, 5_000))
        assertEquals(60_000L, BackgroundPolls.nextDelay(BackgroundPolls.Kind.LIVE, 20_000))
        assertEquals(4_000L, BackgroundPolls.nextDelay(BackgroundPolls.Kind.PLAYER, 31_000))
        assertEquals(49_000L, BackgroundPolls.nextDelay(BackgroundPolls.Kind.LIVE, 31_000))
        for (now in 0L..180_000L step 137) assertNotEquals(
            now + BackgroundPolls.nextDelay(BackgroundPolls.Kind.PLAYER, now),
            now + BackgroundPolls.nextDelay(BackgroundPolls.Kind.LIVE, now))
    }

    @Test fun `slow upstream queries serialize and cancellation releases the shared gate`() = runBlocking<Unit> {
        val active = AtomicInteger()
        val peak = AtomicInteger()
        coroutineScope {
            repeat(2) { launch(Dispatchers.Default) {
                BackgroundPolls.query {
                    peak.accumulateAndGet(active.incrementAndGet(), ::maxOf)
                    delay(40)
                    active.decrementAndGet()
                }
            } }
        }
        assertEquals(1, peak.get())
        val entered = CompletableDeferred<Unit>()
        val job = launch { BackgroundPolls.query { entered.complete(Unit); awaitCancellation() } }
        entered.await(); job.cancelAndJoin()
        withTimeout(3_000) { BackgroundPolls.query { assertTrue(true) } }
    }
}
