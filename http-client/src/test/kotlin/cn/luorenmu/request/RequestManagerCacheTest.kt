package cn.luorenmu.request

import cn.luorenmu.request.api.PakeApi
import cn.luorenmu.request.api.entity.module.CacheTime
import com.sun.net.httpserver.HttpServer
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals

class RequestManagerCacheTest {
    @Test
    fun `failed responses and uncached queries remain retryable`() = runBlocking {
        val requests = AtomicInteger()
        val path = "/retry-test-${UUID.randomUUID()}"
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext(path) { exchange ->
            val first = requests.incrementAndGet() == 1
            val body = (if (first) "missing" else "fixture").toByteArray()
            exchange.responseHeaders.add("Cache-Control", "no-store")
            exchange.sendResponseHeaders(if (first) 404 else 200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val api = object : PakeApi(path, cacheTime = CacheTime.HOUR) {
                override var baseUrl = "http://127.0.0.1:${server.address.port}"
            }
            assertEquals(404, RequestManager.call(api).status.value)
            assertEquals("fixture", RequestManager.call(api).bodyAsText())
            assertEquals("fixture", RequestManager.call(api).bodyAsText())
            assertEquals(2, requests.get())
            val uncached = object : PakeApi(path, cacheTime = CacheTime.NULL) {
                override var baseUrl = api.baseUrl
            }
            repeat(2) { assertEquals("fixture", RequestManager.call(uncached).bodyAsText()) }
            assertEquals(4, requests.get())
        } finally {
            server.stop(0)
            RequestManager.cacheMap.getValue(CacheTime.HOUR).invalidate(path)
        }
    }

    @Test
    fun `concurrent cold queries share one upstream request`() = runBlocking {
        val requests = AtomicInteger()
        val path = "/cache-test-${UUID.randomUUID()}"
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext(path) { exchange ->
            requests.incrementAndGet()
            Thread.sleep(100)
            val body = "fixture".toByteArray()
            exchange.responseHeaders.add("Cache-Control", "no-store")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val api = object : PakeApi(path, cacheTime = CacheTime.HOUR) {
                override var baseUrl = "http://127.0.0.1:${server.address.port}"
            }
            val elapsed = measureTimeMillis {
                val bodies = coroutineScope {
                    (1..12).map { async { RequestManager.call(api).bodyAsText() } }.awaitAll()
                }
                assertEquals(List(12) { "fixture" }, bodies)
            }
            println("[PERF] concurrent-query requests=${requests.get()} elapsed=${elapsed}ms")
            assertEquals(1, requests.get())
        } finally {
            server.stop(0)
            RequestManager.cacheMap.getValue(CacheTime.HOUR).invalidate(path)
        }
    }
}
