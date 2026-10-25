package cn.luorenmu.plugins.grouppet

import cn.luorenmu.common.util.NickNameUtil
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import love.forte.simbot.message.Message
import love.forte.simbot.message.plus
import love.forte.simbot.message.toText
import java.time.Clock
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import cn.luorenmu.plugins.qqmedia.QQGroupPermissionResult
import cn.luorenmu.plugins.qqmedia.QQGroupPermissionStatus

internal class GroupPetService(
    private val repository: GroupPetRepository,
    private val source: GroupPetSource,
    private val render: suspend (PetSnapshot) -> Message,
    private val send: suspend (String, Message) -> Unit,
    private val clock: Clock = Clock.systemUTC(),
    private val permission: suspend (String) -> QQGroupPermissionResult,

) {
    private val tickMutex = Mutex()


    fun list(group: String): List<PetSubscription> = repository.list(group).filter { it.expiresAt > clock.millis() }
    fun remove(group: String, nickname: String): Boolean = repository.list(group)
        .firstOrNull { it.nickname.equals(nickname, true) || it.playerId.toString() == nickname }
        ?.let { repository.remove(group, it.playerId) } ?: false

    suspend fun subscribe(group: String, requester: String, nickname: String): String {
        if (!NickNameUtil.isValidNickname(nickname)) return "玩家名称格式不合法，请检查后重试。"
        val access = permissionStatus(group)
        if (access.status != QQGroupPermissionStatus.ENABLED) return groupPetPermissionNotice(access)
        if (list(group).any { it.nickname.equals(nickname, true) }) return "该玩家已经是本群群宠，不会重复订阅或延长有效期"
        if (list(group).size >= 3) return "每群最多订阅 3 位群宠，请先取消旧订阅"
        val snapshot = withTimeout(90_000) { source.forSubscription(nickname) }
        require(snapshot.playerId > 0 && snapshot.nickname.isNotBlank()) { "玩家信息无效" }
        val now = clock.millis()
        if (!repository.add(group, requester, snapshot, now)) return "该玩家已经是本群群宠，不会重复订阅或延长有效期"
        return "已将 ${snapshot.nickname} 设为群宠。" +
            "\n有效期 7 天，到期自动停止轮询，可重新订阅。" +
            "\n出现新对局后主动发送最新一局" +
            "\n该功能需要群主打开主动发送消息，否则不生效。" +
            "\n取消：/群宠 取消 ${snapshot.nickname}"
    }

    private suspend fun permissionStatus(group: String): QQGroupPermissionResult = try {
        withTimeout(12_000) { permission(group) }
    } catch (error: TimeoutCancellationException) {
        currentCoroutineContext().ensureActive()
        QQGroupPermissionResult(QQGroupPermissionStatus.TIMEOUT)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        QQGroupPermissionResult(QQGroupPermissionStatus.FAILED)
    }

    suspend fun tick(): Int = tickMutex.withLock {
        var sent = 0
        val permissions = mutableMapOf<String, QQGroupPermissionResult>()
        // One live permission query per group per batch; no local event state is trusted.
        // One fetch/render per player per batch; each group's delivery cursor and lease are independent.
        for ((playerId, rows) in repository.due(clock.millis()).groupBy { it.playerId }) {
            var fetched: Result<PetSnapshot>? = null
            var rendered: Message? = null
            for (row in rows) {
                currentCoroutineContext().ensureActive()
                val owner = UUID.randomUUID().toString()
                if (!repository.claim(row, owner, clock.millis())) continue
                var phase = "查询战绩"
                var inactivityMatchId = row.inactivityMatchId
                try {
                    if (!repository.owns(row, owner, clock.millis())) continue
                    val access = permissions.getOrPut(row.groupId) { permissionStatus(row.groupId) }
                    if (access.status != QQGroupPermissionStatus.ENABLED) {
                        repository.finish(row.copy(nextCheckAt = clock.millis() + 300_000,
                            lastError = groupPetPermissionNotice(access)), owner)
                        continue
                    }
                    if (fetched == null) fetched = runCatching { withTimeout(90_000) { source.latest(row.nickname) } }
                    currentCoroutineContext().ensureActive()
                    val snapshot = fetched.getOrThrow()
                    check(snapshot.playerId == playerId) { "PlayerIdentityChanged" }
                    val latest = snapshot.latest
                    val updated = latest != null && (row.lastMatchId == null || latest.id > row.lastMatchId)
                    if (updated) {
                        if (!repository.owns(row, owner, clock.millis())) continue
                        phase = "生成对局图片"
                        if (rendered == null) rendered = withTimeout(90_000) {
                            render(snapshot) +
                                "主人，您的宠物\"${snapshot.nickname}\"刚刚进行了一场对局喵~".toText()
                        }
                        if (!repository.owns(row, owner, clock.millis())) continue
                        phase = "主动发送"
                        withTimeout(60_000) { send(row.groupId, checkNotNull(rendered)) }
                        sent++
                        repository.recordPush(row.groupId, playerId, clock.millis())
                    } else if (latest != null && latest.id == row.lastMatchId && latest.id != inactivityMatchId) {
                        // Only fresh, consistent match evidence can establish inactivity; missing/regressed history is not proof.
                        val notice = PetPollingPolicy.inactivityMessage(snapshot.nickname, latest.startedAt, clock.millis())
                        if (notice != null) {
                            if (!repository.owns(row, owner, clock.millis())) continue
                            phase = "主动发送未游玩提醒"
                            withTimeout(60_000) { send(row.groupId, notice.toText()) }
                            inactivityMatchId = latest.id
                            sent++
                            repository.recordPush(row.groupId, playerId, clock.millis())
                        }
                    }
                    val matchTime = if (updated || latest?.id == row.lastMatchId) latest?.startedAt ?: row.lastMatchTime else row.lastMatchTime
                    val now = clock.millis()
                    repository.finish(row.copy(
                        nickname = snapshot.nickname,
                        lastMatchId = if (updated) latest.id else row.lastMatchId,
                        lastMatchTime = matchTime,
                        nextCheckAt = now + PetPollingPolicy.interval(matchTime, now).toMillis(),
                        failures = 0, lastError = null,
                        inactivityMatchId = inactivityMatchId,
                    ), owner)
                } catch (error: Exception) {
                    // All request/send failures retain subscriptions; plugin shutdown cancellation must escape.
                    currentCoroutineContext().ensureActive()
                    if (error is CancellationException && error !is TimeoutCancellationException) throw error
                    // Only an explicit not-found response from the player query can remove this task.
                    if (phase == "查询战绩" && error is PetPlayerNotFoundException) {
                        repository.discard(row, owner)
                        continue
                    }
                    val failures = (row.failures + 1).coerceAtMost(20)
                    val now = clock.millis()
                    val retryDelay = maxOf(PetPollingPolicy.retry(failures), PetPollingPolicy.interval(row.lastMatchTime, now))
                    repository.finish(row.copy(
                        nextCheckAt = now + retryDelay.toMillis(),
                        failures = failures, lastError = "$phase：${error.javaClass.simpleName}".take(100),
                        inactivityMatchId = inactivityMatchId,
                    ), owner)
                }
            }
        }
        sent + notifyExpired(permissions)
    }

    private suspend fun notifyExpired(permissions: MutableMap<String, QQGroupPermissionResult>): Int {
        var sent = 0
        for (row in repository.expiredDue(clock.millis())) {
            currentCoroutineContext().ensureActive()
            val owner = UUID.randomUUID().toString()
            if (!repository.claimExpiry(row, owner, clock.millis())) continue
            try {
                if (!repository.ownsExpiry(row, owner, clock.millis())) continue
                val access = permissions.getOrPut(row.groupId) { permissionStatus(row.groupId) }
                if (access.status != QQGroupPermissionStatus.ENABLED) {
                    repository.deferExpiry(row, owner, clock.millis() + 300_000, 0, groupPetPermissionNotice(access))
                    continue
                }
                if (!repository.ownsExpiry(row, owner, clock.millis())) continue
                val notice = "主人，\"LoMu\"已经暂停对您的宠物\"${row.nickname}\"监视，如果还需要可以再跟我说哦。为了\"LoMu\"的身心健康，这是必要的喵！"
                withTimeout(60_000) { send(row.groupId, notice.toText()) }
                sent++
                // Persist completion before optional statistics; completed rows cannot be replayed after restart.
                repository.completeExpiry(row, owner)
                repository.recordPush(row.groupId, row.playerId, clock.millis())
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                if (error is CancellationException && error !is TimeoutCancellationException) throw error
                val failures = if (row.expiryNoticeNextAt == 0L) 1 else (row.failures + 1).coerceAtMost(20)
                repository.deferExpiry(row, owner, clock.millis() + PetPollingPolicy.retry(failures).toMillis(),
                    failures, "到期提醒：${error.javaClass.simpleName}")
            }
        }
        return sent
    }
}
