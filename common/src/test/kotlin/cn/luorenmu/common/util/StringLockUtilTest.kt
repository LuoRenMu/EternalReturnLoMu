package cn.luorenmu.common.util

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 *
 * @author LoMu
 * Date 2026/8/16 15:30
 */
class StringLockUtilTest {
    @Test
    fun serializesEveryConcurrentUserOfTheSameKey() = runBlocking {
        val active = AtomicInteger()
        val maximumActive = AtomicInteger()

        (1..100).map {
            async {
                StringLockUtil.withKeyLock("same-resource") {
                    val current = active.incrementAndGet()
                    maximumActive.updateAndGet { maxOf(it, current) }
                    delay(1)
                    active.decrementAndGet()
                }
            }
        }.awaitAll()

        assertEquals(1, maximumActive.get())
    }
}
