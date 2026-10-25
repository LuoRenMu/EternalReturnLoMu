package cn.luorenmu.plugins.grouppet

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock

/** Shared across subscriptions and polling batches. No group delivery state is cached here. */
internal class SharedGroupPetSource(
    private val upstream: GroupPetSource,
    private val clock: Clock = Clock.systemUTC(),
) : GroupPetSource {
    private data class Entry(val snapshot: PetSnapshot, val expiresAt: Long)
    private val cache = LinkedHashMap<String, Entry>(16, 0.75f, true)
    // Fixed lock stripes bound memory and coalesce concurrent requests for the same nickname.
    private val locks = Array(64) { Mutex() }

    override suspend fun latest(nickname: String): PetSnapshot = fetch(nickname, false)
    override suspend fun forSubscription(nickname: String): PetSnapshot = fetch(nickname, true)

    private suspend fun fetch(nickname: String, fresh: Boolean): PetSnapshot =
        locks[(nickname.hashCode() and Int.MAX_VALUE) % locks.size].withLock {
            synchronized(cache) { cache[nickname]?.takeIf { !fresh && it.expiresAt > clock.millis() } }
                ?.let { return@withLock it.snapshot }
            // Exceptions/cancellation are never cached; normal retry/backoff remains with each subscription.
            if (fresh) synchronized(cache) {
                val previous = cache[nickname]
                cache.entries.removeIf { it.key == nickname || (previous != null && it.value.snapshot.playerId == previous.snapshot.playerId) }
            }
            val snapshot = if (fresh) upstream.forSubscription(nickname) else upstream.latest(nickname)
            val now = clock.millis()
            val entry = Entry(snapshot, now + PetPollingPolicy.interval(snapshot.latest?.startedAt, now).toMillis())
            synchronized(cache) {
                cache.entries.removeIf { it.value.expiresAt <= now }
                cache[nickname] = entry
                cache[snapshot.nickname] = entry
                while (cache.size > 512) cache.remove(cache.keys.first())
            }
            snapshot
        }
}
