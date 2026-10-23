package cn.luorenmu.plugins.qqmedia

/** Bounded, monotonic callback deduplication bundled with plugins, not tied to a host core version. */
class QQInteractionGuard(
    private val ttlMillis: Long = 10 * 60 * 1_000,
    private val maxEntries: Int = 10_000,
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    private val seen = LinkedHashMap<String, Long>()

    init { require(ttlMillis > 0); require(maxEntries > 0) }

    @Synchronized
    fun shouldProcess(id: String): Boolean {
        val now = nowMillis()
        val iterator = seen.entries.iterator()
        while (iterator.hasNext()) {
            if (now - iterator.next().value < ttlMillis) break
            iterator.remove()
        }
        if (seen.containsKey(id)) return false
        if (seen.size >= maxEntries) seen.remove(seen.keys.first())
        seen[id] = now
        return true
    }
}
