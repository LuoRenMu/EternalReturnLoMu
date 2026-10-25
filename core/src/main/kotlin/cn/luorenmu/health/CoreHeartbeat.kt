package cn.luorenmu.health

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import java.time.Clock
import java.time.Instant

/** 探针只返回可公开的说明；异常 message/响应正文绝不进入状态、邮件或日志。 */
data class HealthResult(val healthy: Boolean, val detail: String = if (healthy) "正常" else "异常")

class HealthCheck(val id: String, val title: String, val probe: suspend () -> HealthResult)

fun interface HealthNotifier {
    suspend fun send(alert: HealthAlert)
}

data class HealthAlert(val adapter: String, val time: Instant, val failures: List<HealthCheckStatus>)

@Serializable
data class HealthCheckStatus(
    val id: String,
    val title: String,
    val healthy: Boolean? = null,
    val consecutiveFailures: Int = 0,
    val detail: String = "尚未检查",
)

@Serializable
data class HeartbeatStatus(
    val enabled: Boolean,
    val state: String,
    val checks: List<HealthCheckStatus> = emptyList(),
    val lastCheckedAt: String? = null,
    val lastAlertAt: String? = null,
    val lastNotificationError: String? = null,
)

/** 单次串行扫描、连续失败确认、全局告警限频；生命周期由宿主 Application 管理。 */
class CoreHeartbeat(
    private val config: HeartbeatConfig,
    private val adapter: String,
    private val checks: List<HealthCheck>,
    private val notifier: HealthNotifier,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val logger = KotlinLogging.logger {}
    private val scanMutex = Mutex()
    private var job: Job? = null
    private var lastAlert: Instant? = null
    private var lastAttempt: Instant? = null

    @Volatile
    private var snapshot = HeartbeatStatus(
        config.enabled, if (config.enabled) "starting" else "disabled",
        checks.map { HealthCheckStatus(it.id, it.title) },
    )

    init {
        require(config.intervalSeconds in 1..86400)
        require(config.startupGraceSeconds in 0..86400)
        require(config.timeoutSeconds in 1..300)
        require(config.failureThreshold in 1..100)
        require(config.alertCooldownSeconds in 1..604800)
        require(config.notificationRetrySeconds in 1..86400)
        require(checks.isNotEmpty() && checks.map { it.id }.distinct().size == checks.size)
    }

    fun status(): HeartbeatStatus = snapshot

    @Synchronized
    fun start(scope: CoroutineScope): Job? {
        if (!config.enabled) return null
        job?.takeIf { it.isActive }?.let { return it }
        return scope.launch(CoroutineName("core-heartbeat")) {
            try {
                delay(config.startupGraceSeconds * 1000L)
                while (isActive) {
                    checkOnce()
                    delay(config.intervalSeconds * 1000L)
                }
            } finally {
                snapshot = snapshot.copy(state = "stopped")
            }
        }.also { job = it }
    }

    @Synchronized
    fun stop() { job?.cancel() }

    internal suspend fun checkOnce(): HeartbeatStatus = scanMutex.withLock {
        if (!config.enabled) return@withLock snapshot
        val previous = snapshot.checks.associateBy { it.id }
        val results = checks.map { check ->
            val result = try {
                withTimeoutOrNull(config.timeoutSeconds * 1000L) { check.probe() }
                    ?: HealthResult(false, "检查超时")
            } catch (cancelled: CancellationException) {
                currentCoroutineContext().ensureActive()
                // 某个 SDK 的子任务取消不应杀死整个心跳；宿主取消仍由 ensureActive 传播。
                HealthResult(false, "检查被中断")
            } catch (error: Exception) {
                HealthResult(false, "检查失败：${error.javaClass.simpleName}")
            }
            val count = if (result.healthy) 0 else
                ((previous[check.id]?.consecutiveFailures ?: 0) + 1).coerceAtMost(1000000)
            HealthCheckStatus(check.id, check.title, result.healthy, count, result.detail)
        }
        val now = clock.instant()
        val failures = results.filter { it.consecutiveFailures >= config.failureThreshold }
        snapshot = snapshot.copy(
            state = if (results.all { it.healthy == true }) "healthy" else "degraded",
            checks = results, lastCheckedAt = now.toString(),
        )
        if (failures.isNotEmpty() && elapsed(lastAlert, now, config.alertCooldownSeconds) &&
            elapsed(lastAttempt, now, config.notificationRetrySeconds)) {
            lastAttempt = now
            try {
                notifier.send(HealthAlert(adapter, now, failures))
                lastAlert = now
                snapshot = snapshot.copy(lastAlertAt = now.toString(), lastNotificationError = null)
                logger.warn { "心跳异常邮件已发送：${failures.joinToString { it.id }}" }
            } catch (cancelled: CancellationException) {
                currentCoroutineContext().ensureActive()
                snapshot = snapshot.copy(lastNotificationError = "CancellationException")
                logger.warn { "心跳异常邮件发送被中断；稍后重试" }
            } catch (error: Exception) {
                // SMTP/SDK 异常可能带凭据、请求正文或 URL，禁止输出原始异常。
                val category = error.javaClass.simpleName
                snapshot = snapshot.copy(lastNotificationError = category)
                logger.warn { "心跳异常邮件发送失败：$category；稍后重试" }
            }
        }
        snapshot
    }

    private fun elapsed(previous: Instant?, now: Instant, seconds: Int): Boolean =
        previous == null || !now.isBefore(previous.plusSeconds(seconds.toLong()))
}
