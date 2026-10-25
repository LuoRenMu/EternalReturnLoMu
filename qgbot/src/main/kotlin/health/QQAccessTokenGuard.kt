package cn.luorenmu.qqbot.health

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/** 不使用 data class，避免授权凭据经调试字符串泄漏。 */
internal class QQTokenLease(val token: String, val expiresInSeconds: Int) {
    override fun toString(): String = "QQTokenLease(token=********, expiresInSeconds=$expiresInSeconds)"
}

/**
 * SDK 4.5.0 的刷新协程可能在一次异常后结束。独立维护可重试的租约，并在其到期前续约。
 * 通过公开 HttpSend 插件更新实际出站鉴权，不反射写 SDK 私有字段，也不更换 Bot/监听器。
 */
internal class QQAccessTokenGuard(
    private val fetch: suspend () -> QQTokenLease,
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000 },
    private val requestTimeoutMillis: Long = 30_000,
    private val initialRetryMillis: Long = 5_000,
    private val maxRetryMillis: Long = 60_000,
) {
    private class CachedToken(val token: String, val expiresAt: Long)
    private val logger = KotlinLogging.logger {}
    private val mutex = Mutex()
    private var job: Job? = null
    private var failures = 0

    @Volatile
    private var cached: CachedToken? = null

    init {
        require(requestTimeoutMillis > 0 && initialRetryMillis > 0 && maxRetryMillis >= initialRetryMillis)
    }

    fun currentToken(): String? = cached?.takeIf { it.expiresAt > nowMillis() }?.token

    @Synchronized
    fun start(scope: CoroutineScope): Job {
        job?.takeIf { it.isActive }?.let { return it }
        return scope.launch(CoroutineName("qq-token-guard")) {
            while (isActive) {
                val refreshed = refreshNow()
                val wait = if (refreshed) nextRefreshMillis() else retryMillis()
                delay(wait)
            }
        }.also { job = it }
    }

    @Synchronized
    fun stop() { job?.cancel() }

    internal suspend fun refreshNow(): Boolean = mutex.withLock {
        try {
            val started = nowMillis()
            val lease = withTimeoutOrNull(requestTimeoutMillis) { fetch() }
                ?: throw TokenRefreshTimeoutException()
            require(lease.token.isNotBlank() && lease.expiresInSeconds in 1..86400) { "QQ token 响应无效" }
            val next = CachedToken(lease.token, started + lease.expiresInSeconds * 1000L)
            require(next.expiresAt > nowMillis()) { "QQ token 响应已过期" }
            val changed = cached?.token != next.token
            cached = next
            if (changed || failures > 0) logger.info { "QQ token 维护成功，出站鉴权已更新" }
            failures = 0
            true
        } catch (cancelled: CancellationException) {
            currentCoroutineContext().ensureActive()
            failed("CancellationException")
        } catch (error: Exception) {
            failed(error.javaClass.simpleName)
        }
    }

    private fun failed(category: String): Boolean {
        failures = (failures + 1).coerceAtMost(100)
        logger.warn { "QQ token 维护失败：$category；${retryMillis() / 1000} 秒后重试" }
        return false
    }

    private fun retryMillis(): Long =
        (initialRetryMillis * (1L shl (failures - 1).coerceIn(0, 6))).coerceAtMost(maxRetryMillis)

    private fun nextRefreshMillis(): Long =
        (((cached?.expiresAt ?: nowMillis()) - nowMillis()) * 85 / 100).coerceIn(1000, 3_600_000)

    private class TokenRefreshTimeoutException : RuntimeException()
}

/** 只覆盖指定 QQ API 主机已有的 QQBot 鉴权头；不把凭据带到 token 接口或其他主机。 */
internal fun installQQTokenOverride(client: HttpClient, apiHost: String, token: () -> String?) {
    client.plugin(HttpSend).intercept { request ->
        if (request.url.host == apiHost && request.headers[HttpHeaders.Authorization]?.startsWith("QQBot ") == true) {
            token()?.let { fresh ->
                request.headers.remove(HttpHeaders.Authorization)
                request.headers.append(HttpHeaders.Authorization, "QQBot $fresh")
            }
        }
        execute(request)
    }
}
