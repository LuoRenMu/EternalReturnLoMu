package cn.luorenmu.plugins.test

/** Plugin-local so callback-only hot updates also work on hosts without the newer core message guard. */
internal class TestInteractionGuard(
    private val ttlMillis: Long = 10 * 60 * 1_000,
    private val maxEntries: Int = 10_000,
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    private val seen = LinkedHashMap<String, Long>()

    init {
        require(ttlMillis > 0)
        require(maxEntries > 0)
    }

    @Synchronized
    fun shouldProcess(interactionId: String): Boolean {
        val now = nowMillis()
        val iterator = seen.entries.iterator()
        while (iterator.hasNext()) {
            if (now - iterator.next().value < ttlMillis) break
            iterator.remove()
        }
        if (seen.containsKey(interactionId)) return false
        if (seen.size >= maxEntries) seen.remove(seen.keys.first())
        seen[interactionId] = now
        return true
    }
}
