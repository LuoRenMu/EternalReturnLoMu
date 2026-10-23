package cn.luorenmu.plugins.test

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import io.github.oshai.kotlinlogging.KotlinLogging

internal class BiliLiveMonitor(
    private val repository: BiliLiveRepository,
    private val fetch: suspend (Long) -> BiliRoom,
    private val send: suspend (String, String) -> Unit,
    private val pause: suspend (Long) -> Unit = { delay(it) },
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private val log = KotlinLogging.logger {}
    private val retry = mutableMapOf<Long, Pair<Int, Long>>()
    private var scope: CoroutineScope? = null

    @Synchronized fun start() {
        if (scope != null) return
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { worker -> worker.launch {
            delay(5_000)
            while (isActive) {
                try { tick() } catch (e: Exception) {
                    currentCoroutineContext().ensureActive()
                    log.warn { "B站直播扫描失败：${e.javaClass.simpleName}" }
                }
                delay(60_000)
            }
        } }
    }
    @Synchronized fun stop() { scope?.cancel(); scope = null }

    suspend fun command(group: String, action: String, argument: String): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (action == "列表") return@withLock repository.rows().filter { it.group == group }
                .joinToString("\n") { "${it.room}：${if (it.status == 1) "直播中" else "未开播／轮播"}" }
                .ifEmpty { "本群暂无直播监听" }
            val id = biliRoomId(if (action == "取消") argument else action)
                ?: return@withLock "用法：/test 直播 房间号或直播间链接\n/test 直播 列表\n/test 直播 取消 房间号"
            if (action == "取消") {
                // A canonical room ID can be cancelled even while Bilibili is unavailable.
                if (repository.remove(group,id)) return@withLock "已取消直播监听：$id"
                val canonical = fetch(id).id
                return@withLock if (repository.remove(group,canonical)) "已取消直播监听：$canonical" else "本群未监听该直播间"
            }
            val rows = repository.rows()
            val room = fetch(id)
            if (rows.any { it.group == group && it.room == room.id }) return@withLock "本群已监听该直播间：${room.id}"
            if (rows.count { it.group == group } >= 3) return@withLock "每群最多监听 3 个直播间"
            if (rows.map { it.room }.distinct().size >= 50 && rows.none { it.room == room.id }) return@withLock "测试直播监听总量已满"
            repository.add(group,room)
            "已监听直播间 ${room.id}：${room.title}\n当前${if (room.status == 1) "直播中" else "未开播／轮播"}，每 60 秒轮询，后续开播时提醒。\n首次订阅不补发；群主需在机器人页面开启主动发送消息。\n取消：/test 直播 取消 ${room.id}"
        }
    }

    suspend fun tick() = mutex.withLock {
        val grouped = repository.rows().groupBy { it.room }
        retry.keys.retainAll(grouped.keys)
        for ((id, rows) in grouped) {
            if ((retry[id]?.second ?: 0) > now()) continue
            val room = try { fetch(id).also { check(it.id == id); retry.remove(id) } } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                if (e is CancellationException) throw e
                val failures = ((retry[id]?.first ?: 0) + 1).coerceAtMost(6)
                retry[id] = failures to (now() + (60_000L shl failures).coerceAtMost(3_600_000))
                log.warn { "B站直播查询失败：${e.javaClass.simpleName}，已退避" }
                pause(1_000)
                continue
            }
            for (row in rows) {
                val newLive = room.status == 1 && (if (room.session.isNotBlank()) room.session != row.session else row.status != 1)
                // Persist before send: reloads and ambiguous QQ failures must not duplicate the same session.
                if (!repository.advance(row,room) || !newLive) continue
                try {
                    withTimeout(30_000) { send(row.group,"B站开播提醒\n${room.title}\n直播间：${room.id}\nhttps://live.bilibili.com/${room.id}") }
                } catch (e: Exception) {
                    currentCoroutineContext().ensureActive()
                    if (e is CancellationException && e !is TimeoutCancellationException) throw e
                    log.warn { "B站开播提醒失败（本场不重发）：${e.javaClass.simpleName}" }
                }
                pause(6_100)
            }
            pause(1_000)
        }
    }
}
