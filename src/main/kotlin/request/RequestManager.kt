package cn.luorenmu.request

import cn.luorenmu.common.util.PathUtils
import cn.luorenmu.request.api.Api
import cn.luorenmu.request.api.EternalReturnOpenApi
import cn.luorenmu.request.api.PakeResourceApi
import com.github.benmanes.caffeine.cache.Caffeine
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.call.*
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 *
 * @author LoMu
 * Date 2025/10/25 16:30
 */
object RequestManager {
    private val log = KotlinLogging.logger {}
    private val lockCache = Caffeine.newBuilder()
        .maximumSize(300)
        .expireAfterAccess(10, TimeUnit.MINUTES)
        .build<String, Mutex>()

    private val cache = Caffeine.newBuilder()
        .maximumSize(1000)
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .build<String, HttpResponse>()


    private val client = HttpClient(CIO) {
        engine {
            maxConnectionsCount = 1000
            endpoint {
                maxConnectionsPerRoute = 100
                pipelineMaxSize = 20
                keepAliveTime = 5000
                connectTimeout = 10_000
                connectAttempts = 3
            }

        }
        install(HttpRequestRetry.Plugin) {
            maxRetries = 5
            retryOnServerErrors(maxRetries = 3)

            retryOnExceptionIf { request, cause ->
                when (cause) {
                    is SocketTimeoutException,
                    is ConnectTimeoutException,
                    is ClientRequestException,
                    is java.net.ConnectException,
                    is HttpRequestTimeoutException,
                        -> true

                    else -> false
                }
            }
            exponentialDelay()
            delayMillis { retry ->
                retry * 1000L
            }

        }
        install(HttpCache.Companion) {
            //val cacheFile = Files.createDirectories(Path(ReadWriteFile.currentPathFileName("/cache"))).toFile()
            //publicStorage(FileStorage(cacheFile))
        }
        install(HttpTimeout.Plugin) {
            requestTimeoutMillis = 10000
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
        install(HttpCache) {
            val resourcesPathResolve = PathUtils.resourcesPathResolve("cache")
            if (!resourcesPathResolve.toFile().exists()) {
                Files.createDirectory(resourcesPathResolve)
            }
            val fileDirectory = resourcesPathResolve.toFile()
            publicStorage(FileStorage(fileDirectory))
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
        log.debug { "Calling method ${api.method} ==> $requestUrl" }
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

    suspend fun call(api: Api): HttpResponse {
        /**
         * 请求缓冲 太多的请求可能造成
         */
        if (api is EternalReturnOpenApi) {
            delay(Random.nextLong(1, 1000))
        }

        val response = executeRequest(api)
        if (api is EternalReturnOpenApi) {
            val jsonObject = response.body<JsonObject>().jsonObject
            jsonObject["message"]?.jsonPrimitive?.content?.let {
                if (it == "Too Many Requests") {
                    delay(Random.nextLong(3000, 6000))
                    return call(api)
                }
            }
        }
        return response
    }

    suspend fun callStream(api: PakeResourceApi, path: Path) {
        val file = path.toFile()
        if (file.exists() && file.length() > 0) {
            return
        }
        try {
            file.parentFile?.let { parent ->
                if (!parent.exists()) {
                    parent.mkdirs()
                }
            }
            log.debug { "call stream file: $path from ${api.baseUrl}${api.url}" }
            val response = call(api)

            val readBytes = response.readBytes()
            FileOutputStream(file).use { output ->
                output.write(readBytes)
            }
        } catch (e: Exception) {

            log.error(e) { "Failed to download file: $path from ${api.baseUrl}${api.url} => $e" }
            if (file.exists() && file.length() == 0L) {
                file.delete()
            }
            throw e
        }
    }
}