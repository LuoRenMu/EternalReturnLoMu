package cn.luorenmu.common.util

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 *
 * @author LoMu
 * Date 2025/11/23 23:17
 */
object StringLockUtil {
    private data class LockEntry(val mutex: Mutex = Mutex(), val users: AtomicInteger = AtomicInteger())

    private val lockMap = ConcurrentHashMap<String, LockEntry>()

    suspend fun <T> withKeyLock(key: String, block: suspend () -> T): T {
        val entry = lockMap.compute(key) { _, current ->
            (current ?: LockEntry()).also { it.users.incrementAndGet() }
        }!!
        return try {
            entry.mutex.withLock { block() }
        } finally {
            if (entry.users.decrementAndGet() == 0) lockMap.remove(key, entry)
        }
    }
}
