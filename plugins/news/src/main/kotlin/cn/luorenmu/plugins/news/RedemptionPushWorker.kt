package cn.luorenmu.plugins.news

import cn.luorenmu.Adapter
import cn.luorenmu.currentAdapter
import cn.luorenmu.common.util.PathUtils
import cn.luorenmu.plugins.qqmedia.QQGroupPushPermissions
import cn.luorenmu.plugins.qqmedia.QQProactiveGroupSender
import cn.luorenmu.plugins.qqmedia.SimbotQQRuntime
import cn.luorenmu.repository.NewsRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import love.forte.simbot.component.qguild.event.*
import love.forte.simbot.event.EventListenerRegistrationHandle
import love.forte.simbot.event.EventResult
import love.forte.simbot.event.listen
import love.forte.simbot.message.toText
import org.koin.java.KoinJavaComponent.inject
import java.time.Instant

/** News owns the entire coupon lifecycle. It never loads or calls the group-pet plugin. */
internal class RedemptionPushWorker {
    // Retain the existing file/table names to preserve previously claimed sends after extraction.
    private val path by lazy { PathUtils.pathResolve(paths = arrayOf("data", "group-pet.db")) }
    private val permissions by lazy { QQGroupPushPermissions(path) }
    private val news: NewsRepository by inject(NewsRepository::class.java)
    private val log = KotlinLogging.logger {}
    private val listeners = mutableListOf<EventListenerRegistrationHandle>()
    private var scope: CoroutineScope? = null
    private var lastSendNanos: Long? = null
    private val service by lazy {
        RedemptionPushService(RedemptionPushStore(path), { news.findRedemptionCodesForPush() },
            permissions::pushGroups, permissions::canPush, { push ->
                lastSendNanos?.let { previous ->
                    val remaining = 6_100 - (System.nanoTime() - previous) / 1_000_000
                    if (remaining > 0) delay(remaining)
                }
                check(permissions.canPush(push.group)) { "GroupPushPermissionUnavailable" }
                check(System.currentTimeMillis() < push.expiresAt) { "PushExpired" }
                lastSendNanos = System.nanoTime()
                QQProactiveGroupSender.send(SimbotQQRuntime.requireBot(), push.group, push.body.toText())
            })
    }

    @Synchronized fun start() {
        if (currentAdapter != Adapter.QG_BOT || scope != null) return
        val dispatcher = checkNotNull(SimbotQQRuntime.applicationOrNull()?.eventDispatcher)
        listeners += dispatcher.listen<QGGroupMsgReceiveEvent> { event ->
            permission(event, true); EventResult.empty()
        }
        listeners += dispatcher.listen<QGGroupMsgRejectEvent> { event ->
            permission(event, false); EventResult.empty()
        }
        listeners += dispatcher.listen<QGGroupDelRobotEvent> { event ->
            permission(event, false); EventResult.empty()
        }
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob()).also { worker ->
            worker.launch {
                delay(10_000)
                while (isActive) {
                    try {
                        val count = service.tick()
                        if (count > 0) log.info { "兑换码提醒已发送：$count 条" }
                    } catch (error: Exception) {
                        currentCoroutineContext().ensureActive()
                        log.warn { "兑换码推送扫描失败：${error.javaClass.simpleName}" }
                    }
                    delay(60_000)
                }
            }
        }
    }

    @Synchronized fun stop() {
        listeners.forEach { it.dispose() }
        listeners.clear()
        scope?.cancel()
        scope = null
    }

    private fun permission(event: QGGroupManagementEvent, allowed: Boolean) {
        val data = event.sourceEventEntity
        val timestamp = data.timestamp.toLongOrNull()?.let { it * 1000 }
            ?: runCatching { Instant.parse(data.timestamp).toEpochMilli() }.getOrNull()
        if (timestamp == null && allowed) return
        permissions.updatePermission(data.groupOpenid, allowed, timestamp ?: System.currentTimeMillis())
    }
}
