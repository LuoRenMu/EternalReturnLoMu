package cn.luorenmu.request

import cn.luorenmu.common.util.PathUtils
import cn.luorenmu.common.util.ResourceCheckUtil
import cn.luorenmu.common.util.StringLockUtil.withKeyLock
import cn.luorenmu.exception.ForbiddenException
import cn.luorenmu.request.api.Api
import cn.luorenmu.request.api.PakeResourceApi
import cn.luorenmu.request.api.ResourceApi
import cn.luorenmu.request.api.entity.module.CacheTime
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.engine.java.Java
import io.ktor.client.network.sockets.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.cache.*
import io.ktor.client.plugins.cache.storage.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.printStack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.EOFException
import java.io.IOException
import java.nio.file.Path

/**
 *
 * @author LoMu
 * Date 2025/10/25 16:30
 */
object RequestManager {
    private val log = KotlinLogging.logger {}
    const val RETRY_COUNT = 5
    /**
     * public 用于在赛季更新时清空缓存
     */
    val cacheMap = run {
        val map = mutableMapOf<CacheTime, Cache<String, HttpResponse>>()
        for (time in CacheTime.entries) {
            map[time] = Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(time.duration, time.unit)
                .build()
        }
        map
    }

    private val client = createHttpClient()

    // Keep business HTTP off CIO's ConnectionFactory permit bookkeeping (KTOR-5992).
    // Explicit selection also avoids ServiceLoader order deciding the transport in the application JAR.
    internal fun createHttpClient() = HttpClient(Java) {
        install(HttpTimeout) {
            requestTimeoutMillis = 60000
            connectTimeoutMillis = 60000
            socketTimeoutMillis = 60000
        }
        install(HttpRequestRetry) {
            maxRetries = RETRY_COUNT
            retryIf { _, response -> response.status.value in 500..599 }

            retryOnExceptionIf { _, cause ->
                isRetryableRequestException(cause)
            }
            exponentialDelay()

        }
        install(HttpCache) {
            val file = PathUtils.resourcesPathResolve("cache").toFile()
            if (!file.exists()) {
                file.mkdirs()
            }
            publicStorage(FileStorage(file))
        }
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true  // 忽略 JSON 中的未知字段
                isLenient = true          // 允许非标准 JSON
                encodeDefaults = true     // 编码默认值
                explicitNulls = false     // 不编码 null 值
                coerceInputValues = true  // 强制转换输入值
                allowStructuredMapKeys = true // 允许结构化 Map 键
            })
        }

        defaultRequest {
            header(
                HttpHeaders.UserAgent,
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36 Edg/126.0.0.0"
            )
        }
    }

    internal fun isRetryableRequestException(exception: Throwable): Boolean {
        // Caller cancellation must not become another request merely because closing its socket caused IO.
        if (exception is CancellationException) return false
        var current: Throwable? = exception
        val visited = mutableSetOf<Throwable>()
        while (current != null && visited.add(current)) {
            if (current is EOFException ||
                current is IOException ||
                current is ConnectTimeoutException ||
                current is HttpRequestTimeoutException ||
                current is ClosedReceiveChannelException
            ) {
                return true
            }
            current = current.cause
        }
        return false
    }

    @PublishedApi
    internal suspend fun <T> retryTransientRequest(
        maxRetries: Int = RETRY_COUNT,
        onRetry: () -> Unit = {},
        block: suspend () -> T,
    ): T {
        var retry = 0
        while (true) {
            try {
                return block()
            } catch (e: Exception) {
                if (e is ForbiddenException) throw e
                if (!isRetryableRequestException(e)) throw e
                if (retry >= maxRetries) {
                    throw ForbiddenException("DakGG服务器连接不稳定，请稍后再试").also { it.initCause(e) }
                }
                retry++
                onRetry()
                log.warn(e) { "Transient HTTP failure, retrying request ($retry/$maxRetries)" }
                delay((250L shl (retry - 1)).coerceAtMost(4_000L))
            }
        }
    }

    private suspend fun executeRequest(api: Api): HttpResponse {
        val requestUrl = api.baseUrl + api.url
        return client.request {
            url(requestUrl)
            method = api.method
            if (api.body.isNotEmpty()) {
                setBody(api.body)
            }
            api.headers.let { reqHeaders ->
                reqHeaders.forEach { h ->
                    header(h.key, h.value)
                }
            }
        }
    }

    suspend fun requestRetryCall(api: Api,retryCount: Int = 0): HttpResponse {
        try {
            return call(api);
        }catch (e: Exception){
            if (retryCount >= RETRY_COUNT) {
                log.debug { "retry count exceed limit ${e.printStackTrace()}" }
                throw ForbiddenException("已超过最大重试次数，DakGG服务器拒绝了本次请求")
            }
           return requestRetryCall(api, retryCount + 1)
        }
    }
     suspend fun call(api: Api): HttpResponse {
        log.debug { "call ${api.method.value} ${api.baseUrl}${api.url}" }
        val cache = if (api !is ResourceApi && api.method == HttpMethod.Get && api.cacheTime != CacheTime.NULL)
            cacheMap.getValue(api.cacheTime) else null
        cache?.getIfPresent(api.url)?.let { return it }
        return withKeyLock(api.url) {
            // A cold caller may have filled the cache while this caller waited for the lock.
            cache?.getIfPresent(api.url)?.let { return@withKeyLock it }
            val currentTimeMillis = System.currentTimeMillis()
            try {
                executeRequest(api).also { response ->
                    // Publish before unlocking, so all queued callers can reuse the result.
                    if (response.status.value in 200..207) cache?.put(api.url, response)
                }
            } catch (e: Exception) {
                if (!isRetryableRequestException(e)) throw e
                log.warn(e) { "DakGG request remained unavailable after retries: ${api.baseUrl}${api.url}" }
                throw ForbiddenException("DakGG服务器连接不稳定，请稍后再试").also { it.initCause(e) }
            } finally {
                if (api is ResourceApi) {
                    log.debug { "call stream file: ${api.path} from ${api.baseUrl}${api.url}" }
                } else {
                    log.debug { "call ${api.method.value} ${api.baseUrl}${api.url} take ${System.currentTimeMillis() - currentTimeMillis}ms" }
                }
            }
        }
    }

    suspend fun callStream(api: PakeResourceApi, path: Path, maxRetries: Int = 3) {
        val target = path.toAbsolutePath().normalize()
        if (ResourceCheckUtil.checkResource(target)) return

        withKeyLock("resource:$target") {
            if (ResourceCheckUtil.checkResource(target)) return@withKeyLock
            var lastException: Exception? = null
            repeat(maxRetries) { attempt ->
                try {
                    val readBytes = call(api).readBytes()
                    withContext(Dispatchers.IO) { ResourceCheckUtil.installVerified(target, readBytes) }
                    return@withKeyLock
                } catch (e: Exception) {
                    lastException = e
                    log.warn { "callStream retry ${attempt + 1}/$maxRetries: $target => $e" }
                }
            }
            ResourceCheckUtil.markResourceInvalid(target)
            log.error(lastException) { "Failed to download file: $target from ${api.baseUrl}${api.url}" }
            throw checkNotNull(lastException)
        }
    }
}
