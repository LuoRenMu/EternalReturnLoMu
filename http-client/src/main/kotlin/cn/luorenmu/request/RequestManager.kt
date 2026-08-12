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
import io.ktor.client.engine.cio.*
import io.ktor.client.network.sockets.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.cache.*
import io.ktor.client.plugins.cache.storage.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.network.sockets.SocketTimeoutException
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.printStack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.ConnectException
import java.net.SocketException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID

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

    private val client = HttpClient(CIO) {
        engine {
            maxConnectionsCount = 1000
            endpoint {
                maxConnectionsPerRoute = 100
                pipelineMaxSize = 20
                keepAliveTime = 5000
                connectTimeout = 60_000
                connectAttempts = 3
            }
            requestTimeout = 60000
        }

        install(HttpTimeout) {
            requestTimeoutMillis = 60000
            connectTimeoutMillis = 60000
            socketTimeoutMillis = 60000
        }
        install(HttpRequestRetry) {
            maxRetries = 5
            retryOnServerErrors(maxRetries = 3)

            retryOnExceptionIf { _, cause ->
                when (cause) {
                    is SocketTimeoutException,
                    is ConnectTimeoutException,
                    is ClientRequestException,
                    is ConnectException,
                    is HttpRequestTimeoutException,
                        -> true

                    else -> false
                }
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
        if (api !is ResourceApi && api.cacheTime != CacheTime.NULL) {
            if (api.method == HttpMethod.Get) {
                val cache = cacheMap[api.cacheTime]!!
                cache.getIfPresent(api.url)?.let {
                    log.debug { "Cache hit for ${api.url}" }
                    return it
                }
            }
        }
        val response = withKeyLock(api.url) {
            val currentTimeMillis = System.currentTimeMillis()
            try {
                return@withKeyLock executeRequest(api)
            } catch (e: SocketException) {
                log.debug { "SocketException : ${api.baseUrl}${api.url} ->${e.printStack()}" }
                throw ForbiddenException("DakGG服务器拒绝了本次请求")
            } finally {
                if (api is ResourceApi) {
                    log.debug { "call stream file: ${api.path} from ${api.baseUrl}${api.url}" }
                } else {
                    log.debug { "call ${api.method.value} ${api.baseUrl}${api.url} take ${(System.currentTimeMillis() - currentTimeMillis) / 1000.0}m" }
                }
            }
        }
        if (response.status.value in 200..207) {
            log.debug { "call success ${api.baseUrl}${api.url} => ${response.status.value}" }
            if (api.method == HttpMethod.Get) {
                cacheMap[api.cacheTime]!!.put(api.url, response)
            }
        }

        return response
    }

    suspend fun callStream(api: PakeResourceApi, path: Path, maxRetries: Int = 3) {
        val target = path.toAbsolutePath().normalize()
        if (ResourceCheckUtil.checkResource(target)) return

        withKeyLock("resource:$target") {
            if (ResourceCheckUtil.checkResource(target)) return@withKeyLock
            Files.createDirectories(target.parent)
            val temporary = target.resolveSibling(".${target.fileName}.${UUID.randomUUID()}.part")
            var lastException: Exception? = null
            try {
                repeat(maxRetries) { attempt ->
                    try {
                        val readBytes = call(api).readBytes()
                        require(readBytes.isNotEmpty()) { "Downloaded resource is empty: $target" }
                        withContext(Dispatchers.IO) {
                            Files.write(temporary, readBytes)
                            try {
                                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
                            }
                        }
                        ResourceCheckUtil.markResourceValid(target)
                        return@withKeyLock
                    } catch (e: Exception) {
                        lastException = e
                        Files.deleteIfExists(temporary)
                        log.warn { "callStream retry ${attempt + 1}/$maxRetries: $target => $e" }
                    }
                }
                ResourceCheckUtil.markResourceInvalid(target)
                log.error(lastException) { "Failed to download file: $target from ${api.baseUrl}${api.url}" }
                throw checkNotNull(lastException)
            } finally {
                Files.deleteIfExists(temporary)
            }
        }
    }
}
