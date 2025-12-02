package cn.luorenmu.common.util

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 *
 * @author LoMu
 * Date 2025/11/23 23:17
 */
object StringLockUtil {
    private val lockMap = ConcurrentHashMap<String, Mutex>()




    suspend fun <T> withKeyLock(key: String, block: suspend () -> T): T {
        val mutex = lockMap.compute(key) { _, v -> v ?: Mutex() }!!
        return try {
            mutex.withLock {
                block()
            }
        } finally {
            lockMap.remove(key, mutex)
        }
    }
}