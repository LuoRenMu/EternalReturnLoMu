package cn.luorenmu.plugins.grouppet

import cn.luorenmu.plugins.qqmedia.QQGroupPermissionQuery
import cn.luorenmu.plugins.qqmedia.QQGroupPermissionResult
import cn.luorenmu.plugins.qqmedia.QQGroupPermissionStatus
import cn.luorenmu.plugins.qqmedia.SimbotQQRuntime
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/** Space this plugin's live API checks below the endpoint's 30 QPM quota. Never cache an allow result. */
internal class GroupPetPermissionGate(
    private val lookup: suspend (String) -> QQGroupPermissionResult = {
        QQGroupPermissionQuery.query(SimbotQQRuntime.requireBot(), it)
    },
    private val pause: suspend (Long) -> Unit = { delay(it) },
    private val nanos: () -> Long = System::nanoTime,
) {
    private val mutex = Mutex()
    private var lastAttempt: Long? = null
    suspend fun query(group: String): QQGroupPermissionResult = mutex.withLock {
        lastAttempt?.let { previous ->
            val remaining = 2_100 - (nanos() - previous) / 1_000_000
            if (remaining > 0) pause(remaining)
        }
        lastAttempt = nanos()
        withTimeout(8_000) { lookup(group) }
    }
}

internal fun groupPetPermissionNotice(result: QQGroupPermissionResult): String {
    val reason = when (result.status) {
        QQGroupPermissionStatus.DISABLED -> "本群未开启主动发送消息，暂未执行。"
        QQGroupPermissionStatus.NO_API_PERMISSION -> "无法确认本群主动消息状态（11253：机器人未获接口白名单权限），暂未执行。"
        QQGroupPermissionStatus.NOT_PROVIDED -> "无法确认本群主动消息状态（接口未返回开关），暂未执行。"
        QQGroupPermissionStatus.TIMEOUT -> "主动消息权限查询超时，暂未执行，请稍后重试。"
        QQGroupPermissionStatus.FAILED -> "主动消息权限查询失败，暂未执行，请稍后重试。"
        QQGroupPermissionStatus.ENABLED -> return ""
    }
    return "$reason\n该功能需要群主在机器人页面设置并开启主动发送消息。"
}
