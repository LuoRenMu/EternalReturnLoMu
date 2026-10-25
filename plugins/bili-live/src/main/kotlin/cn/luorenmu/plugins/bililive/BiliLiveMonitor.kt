package cn.luorenmu.plugins.bililive

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import io.github.oshai.kotlinlogging.KotlinLogging
import cn.luorenmu.plugins.qqmedia.BackgroundPolls

internal class BiliLiveMonitor(
    private val repository: BiliLiveRepository,
    private val fetch: suspend (Long) -> BiliRoom,
    private val send: suspend (String, BiliLiveNotice) -> Unit,
    private val permission: suspend (String) -> Boolean,
    private val pause: suspend (Long) -> Unit = { delay(it) },
    private val now: () -> Long = System::currentTimeMillis,
    private val poll: suspend (Long) -> BiliRoom = fetch,
    private val awaitPoll: suspend () -> Unit = { BackgroundPolls.awaitNext(BackgroundPolls.Kind.LIVE) },
) {
    private val mutex = Mutex()
    private val log = KotlinLogging.logger {}
    private val retry = mutableMapOf<Long, Pair<Int, Long>>()
    private var lastDeliveryAt: Long? = null
    private var scope: CoroutineScope? = null

    @Synchronized fun start() {
        if (scope != null) return
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { worker -> worker.launch {
            while (isActive) {
                awaitPoll()
                try { tick() } catch (e: Exception) {
                    currentCoroutineContext().ensureActive()
                    log.warn { "B站直播扫描失败：${e.javaClass.simpleName}" }
                }
            }
        } }
    }
    @Synchronized fun stop() { scope?.cancel(); scope = null }

    suspend fun claim(group: String, sender: String): String = withContext(Dispatchers.IO) {
        if (repository.claim(group, sender)) LIVE_CLAIM_SUCCESS else LIVE_CLAIM_NOTICE
    }
    suspend fun hasClaim(sender: String): Boolean = withContext(Dispatchers.IO) { repository.hasClaim(sender) }

    suspend fun command(group: String, sender: String, action: String, argument: String): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (action == "列表") {
                val active = repository.activeRows().filter { it.group == group }.map { it.room }.toSet()
                return@withLock repository.rows().filter { it.group == group }
                    .joinToString("\n") { "${it.room}：${if (it.room !in active) "历史订阅未绑定（已暂停，请重新订阅或取消）" else if (it.status == 1) "直播中" else "未开播／轮播"}" }
                    .ifEmpty { "本群暂无直播监听" }
            }
            if (action !in setOf("订阅", "取消")) return@withLock LIVE_HELP
            if (action == "订阅" && !repository.hasClaim(sender)) return@withLock LIVE_CLAIM_NOTICE
            val id = biliRoomId(argument) ?: return@withLock LIVE_HELP
            if (action == "取消") {
                // A canonical room ID can be cancelled even while Bilibili is unavailable.
                if (repository.remove(group,id)) return@withLock "已取消直播监听：$id"
                val canonical = fetch(id).id
                return@withLock if (repository.remove(group,canonical)) "已取消直播监听：$canonical" else "本群未监听该直播间"
            }
            val rows = repository.activeRows()
            if (rows.any { it.group == group && it.room == id }) return@withLock "本群已监听该直播间：$id"
            val room = fetch(id)
            // A durable pending cursor survives a restart before the first replay. Never overwrite a duplicate.
            val baseline = if (room.status == 1) room.copy(session = INITIAL_LIVE_PREFIX + room.session) else room
            when (repository.add(group, sender, baseline)) {
                LiveSubscriptionResult.ADDED -> Unit
                LiveSubscriptionResult.ALREADY_SUBSCRIBED -> return@withLock "本群已监听该直播间：${room.id}"
                LiveSubscriptionResult.GROUP_LIMIT -> return@withLock "每群只允许订阅 1 个直播间，请先取消本群原订阅。"
                LiveSubscriptionResult.USER_LIMIT -> return@withLock "每位用户只允许订阅 1 个直播间，同一房间可用于多个群；请先取消你在其他群的不同房间订阅。"
                LiveSubscriptionResult.TOTAL_LIMIT -> return@withLock "直播监听总量已满"
                LiveSubscriptionResult.NOT_CLAIMED -> return@withLock LIVE_CLAIM_NOTICE
            }
            if (room.status == 1) replayInitial(group, room)
            "已监听直播间 ${room.id}：${livePlainText(room.upName).ifBlank { "未知UP主" }}\n" +
                (if (room.status == 1) "当前直播中" else "当前未开播") +
                "\n取消：/主播订阅 取消 ${room.id}"
        }
    }

    /** Use the subscription snapshot now, not another fetch at the next wall-clock polling slot. */
    @Synchronized private fun replayInitial(group: String, room: BiliRoom) {
        scope?.launch {
            try {
                mutex.withLock {
                    val row = repository.activeRows().singleOrNull { it.group == group && it.room == room.id }
                        ?: return@withLock
                    if (!isNewLive(row, room)) return@withLock
                    awaitDeliverySlot()
                    val allowed = try {
                        withTimeout(10_000) { permission(group) }
                    } catch (_: TimeoutCancellationException) {
                        currentCoroutineContext().ensureActive()
                        false
                    } catch (cancelled: CancellationException) { throw cancelled
                    } catch (_: Exception) { false }
                    // Keep the pending cursor when permission is unavailable, so regular polling can recover.
                    if (!allowed || !repository.advance(row, room)) return@withLock
                    deliver(group, BiliLiveNotice(room, now()))
                }
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                if (error is CancellationException) throw error
                log.warn { "B站首次直播补发处理失败：${error.javaClass.simpleName}" }
            }
        }
    }

    private fun isNewLive(row: BiliSubscription, room: BiliRoom): Boolean = room.status == 1 &&
        (row.session.startsWith(INITIAL_LIVE_PREFIX) ||
            (if (room.session.isNotBlank()) room.session != row.session else row.status != 1))

    /** Initial replays and polling share one send gap; waiting never consumes a pending cursor. */
    private suspend fun awaitDeliverySlot() {
        lastDeliveryAt?.let { previous ->
            val remaining = (6_100 - (now() - previous)).coerceAtMost(6_100)
            if (remaining > 0) pause(remaining)
        }
        currentCoroutineContext().ensureActive()
    }

    private suspend fun deliver(group: String, notice: BiliLiveNotice) {
        try {
            withTimeout(30_000) { send(group, notice) }
            repository.recordPush(group, notice.room.id, now())
        } catch (error: Exception) {
            currentCoroutineContext().ensureActive()
            if (error is CancellationException && error !is TimeoutCancellationException) throw error
            log.warn { "B站${if (notice.event == BiliLiveEvent.ENDED) "下播" else "开播"}提醒失败（本次不重发）：${error.javaClass.simpleName}" }
        } finally { lastDeliveryAt = now() }
    }

    suspend fun tick() = mutex.withLock {
        val grouped = repository.activeRows().groupBy { it.room }
        val access = mutableMapOf<String, Pair<Boolean, Long>>()
        suspend fun allowed(group: String): Boolean {
            val cached = access[group]
            if (cached != null && now() - cached.second < 30_000) return cached.first
            val enabled = try { permission(group) } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { false }
            access[group] = enabled to now()
            return enabled
        }
        retry.keys.retainAll(grouped.keys)
        for ((id, rows) in grouped) {
            val permitted = rows.filter { allowed(it.group) }
            if (permitted.isEmpty()) continue
            if ((retry[id]?.second ?: 0) > now()) continue
            val room = try { poll(id).also { check(it.id == id); retry.remove(id) } } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                if (e is CancellationException) throw e
                val failures = ((retry[id]?.first ?: 0) + 1).coerceAtMost(6)
                retry[id] = failures to (now() + (60_000L shl failures).coerceAtMost(3_600_000))
                log.warn { "B站直播查询失败：${e.javaClass.simpleName}，已退避" }
                pause(1_000)
                continue
            }
            for (row in permitted) {
                val newLive = isNewLive(row, room)
                val ended = row.status == 1 && room.status != 1
                if (newLive || ended) awaitDeliverySlot()
                if ((newLive || ended) && !allowed(row.group)) continue
                // Persist before send: reloads and ambiguous QQ failures must not duplicate this event.
                if (!repository.advance(row,room) || (!newLive && !ended)) continue
                val session = if (ended || (row.status == 1 && room.session.isBlank())) row.session.removePrefix(INITIAL_LIVE_PREFIX) else room.session
                val notice = BiliLiveNotice(room.copy(session = session), now(), if (ended) BiliLiveEvent.ENDED else BiliLiveEvent.STARTED)
                deliver(row.group, notice)
            }
            pause(1_000)
        }
    }
}

internal const val INITIAL_LIVE_PREFIX = "@initial-live:"
