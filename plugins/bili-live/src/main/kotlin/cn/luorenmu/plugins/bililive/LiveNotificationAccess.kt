package cn.luorenmu.plugins.bililive

import cn.luorenmu.plugins.qqmedia.QQGroupPermissionQuery
import cn.luorenmu.plugins.qqmedia.QQGroupPermissionResult
import cn.luorenmu.plugins.qqmedia.QQGroupPermissionStatus
import cn.luorenmu.plugins.qqmedia.SimbotQQRuntime
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Unknown, failed, disabled and missing permissions all fail closed. An allow is never persisted. */
internal class LiveNotificationAccess(
    private val lookup: suspend (String) -> QQGroupPermissionResult = {
        QQGroupPermissionQuery.query(SimbotQQRuntime.requireBot(), it)
    },
    private val pause: suspend (Long) -> Unit = { delay(it) },
    private val nanos: () -> Long = System::nanoTime,
) {
    private val mutex = Mutex()
    private var lastAttempt: Long? = null

    suspend fun enabled(group: String): Boolean = try {
        mutex.withLock {
            lastAttempt?.let { previous ->
                val remaining = 2_100 - (nanos() - previous) / 1_000_000
                if (remaining > 0) pause(remaining)
            }
            lastAttempt = nanos()
            withTimeout(8_000) { lookup(group).status == QQGroupPermissionStatus.ENABLED }
        }
    } catch (_: TimeoutCancellationException) {
        currentCoroutineContext().ensureActive()
        false
    } catch (cancelled: CancellationException) { throw cancelled
    } catch (_: Exception) { false }
}

internal const val LIVE_ACCESS_NOTICE = "该命令需要身份为群主，并且打开LoMu的主动消息权限(位于LoMu个人资料设置页面)"
