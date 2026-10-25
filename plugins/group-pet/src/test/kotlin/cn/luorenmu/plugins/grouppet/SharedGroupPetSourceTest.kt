package cn.luorenmu.plugins.grouppet

import kotlinx.coroutines.*
import java.time.Duration
import kotlin.test.*

class SharedGroupPetSourceTest {
    @Test fun `new subscription revalidates upstream even when shared polling cache is fresh`() = runBlocking {
        var exists = true
        var queries = 0
        val source = SharedGroupPetSource({ name ->
            queries++
            if (!exists) throw PetPlayerNotFoundException()
            PetSnapshot(7, name, null)
        }, MutableClock())
        source.latest("백수")
        source.latest("백수")
        assertEquals(1, queries)
        source.forSubscription("백수")
        assertEquals(2, queries)
        exists = false
        assertFailsWith<PetPlayerNotFoundException> { source.forSubscription("백수") }
        assertFailsWith<PetPlayerNotFoundException> { source.latest("백수") }
        assertEquals(4, queries)
    }

    @Test fun `concurrent subscriptions and staggered polls reuse one query until expiry`() = runBlocking {
        val clock = MutableClock()
        var queries = 0
        val source = SharedGroupPetSource({ name ->
            queries++
            delay(10)
            PetSnapshot(7, name, PetMatch(queries.toLong(), clock.millis(), 41))
        }, clock)
        val results = coroutineScope { (1..40).map { async { source.latest("백수") } }.awaitAll() }
        assertEquals(1, queries)
        assertTrue(results.all { it.latest!!.id == 1L })
        clock.advance(Duration.ofSeconds(30))
        assertEquals(1L, source.latest("백수").latest!!.id)
        assertEquals(1, queries)
        clock.advance(Duration.ofSeconds(90))
        assertEquals(2L, source.latest("백수").latest!!.id)
        assertEquals(2, queries)
        source.latest("other")
        assertEquals(3, queries)
    }

    @Test fun `errors are not cached and cold players use long interval`() = runBlocking {
        val clock = MutableClock()
        var queries = 0
        val source = SharedGroupPetSource({ name ->
            queries++
            if (queries == 1) throw PetPlayerNotFoundException()
            PetSnapshot(7, name, null)
        }, clock)
        assertFailsWith<PetPlayerNotFoundException> { source.latest("백수") }
        source.latest("백수")
        clock.advance(Duration.ofHours(5))
        source.latest("백수")
        assertEquals(2, queries)
        clock.advance(Duration.ofHours(1))
        source.latest("백수")
        assertEquals(3, queries)
    }
}
