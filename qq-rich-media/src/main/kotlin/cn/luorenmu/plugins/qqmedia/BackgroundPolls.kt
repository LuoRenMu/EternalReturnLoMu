package cn.luorenmu.plugins.qqmedia

import cn.luorenmu.common.util.StringLockUtil
import kotlinx.coroutines.delay

/** Wall-clock slots do not drift after slow requests or independent plugin reloads. */
object BackgroundPolls {
    enum class Kind(val periodMillis: Long, val offsetMillis: Long) {
        PLAYER(30_000, 5_000),
        LIVE(60_000, 20_000),
    }

    fun nextDelay(kind: Kind, now: Long): Long {
        val elapsed = Math.floorMod(now - kind.offsetMillis, kind.periodMillis)
        return kind.periodMillis - elapsed
    }

    suspend fun awaitNext(kind: Kind) = delay(nextDelay(kind, System.currentTimeMillis()))

    /** StringLockUtil is parent-loaded by the existing host, hence shared across plugin classloaders. */
    suspend fun <T> query(block: suspend () -> T): T =
        StringLockUtil.withKeyLock("background-player-live-upstream-query") {
            try { block() } finally { delay(1_000) }
        }
}
