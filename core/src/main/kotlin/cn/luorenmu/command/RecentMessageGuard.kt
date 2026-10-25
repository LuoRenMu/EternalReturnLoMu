package cn.luorenmu.command

/** Prevents duplicate replies when the same inbound message is delivered more than once. */
class RecentMessageGuard(
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
    fun shouldProcess(messageId: String): Boolean {
        if (messageId.isBlank()) return true
        val now = nowMillis()
        val iterator = seen.entries.iterator()
        while (iterator.hasNext()) {
            if (now - iterator.next().value < ttlMillis) break
            iterator.remove()
        }
        if (seen.containsKey(messageId)) return false
        if (seen.size >= maxEntries) seen.remove(seen.keys.first())
        seen[messageId] = now
        return true
    }
}
