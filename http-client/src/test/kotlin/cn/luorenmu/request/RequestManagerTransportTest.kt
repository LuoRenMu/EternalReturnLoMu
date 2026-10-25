package cn.luorenmu.request

import kotlinx.coroutines.*
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import com.sun.net.httpserver.HttpServer
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.*

class RequestManagerTransportTest {
    @Test fun `business requests explicitly use the JDK engine instead of CIO permit bookkeeping`() {
        RequestManager.createHttpClient().use { client ->
            assertEquals("JavaHttpEngine", client.engine.javaClass.simpleName)
        }
    }

    @Test fun `caller cancellation with an IO cause must not be retried`() = runBlocking<Unit> {
        val cancelled = CancellationException("caller cancelled").apply { initCause(IOException("socket closed by cancellation")) }
        assertFalse(RequestManager.isRetryableRequestException(cancelled))
        var attempts = 0
        val caught = assertFailsWith<CancellationException> {
            RequestManager.retryTransientRequest(maxRetries = 1) { attempts++; throw cancelled }
        }
        assertSame(cancelled, caught)
        assertEquals(1, attempts)
        assertFalse(RequestManager.isRetryableRequestException(IllegalStateException("The number of released permits cannot be greater than 100")))
    }

    @Test fun `JDK transport retains JSON conversion user agent and server error retry`() = runBlocking<Unit> {
        HttpFixture().use { server -> withClient(retries = 1) { client ->
            val value = "中文と한국어"
            val response = client.post(server.url + "/echo") { contentType(ContentType.Application.Json); setBody(Payload(value)) }
            assertEquals(Payload(value), response.body<Payload>())
            assertEquals(value, Json.parseToJsonElement(server.body.get()).jsonObject.getValue("value").jsonPrimitive.content)
            assertTrue(server.userAgent.get().startsWith("Mozilla/5.0"))
            assertEquals("ok", client.get(server.url + "/retry").bodyAsText())
            assertEquals(2, server.retries.get())
        } }
    }

    @Test fun `request timeout and caller cancellation leave the shared transport usable`() = runBlocking<Unit> {
        HttpFixture().use { server -> withClient { client ->
            assertEquals("ok", client.get(server.url + "/ok").bodyAsText()) // warm up before measuring short deadlines
            assertFailsWith<HttpRequestTimeoutException> {
                client.get(server.url + "/slow") { timeout { requestTimeoutMillis = 50 } }.bodyAsText()
            }
            assertFailsWith<TimeoutCancellationException> {
                withTimeout(50) { client.get(server.url + "/slow").bodyAsText() }
            }
            assertEquals("ok", client.get(server.url + "/ok").bodyAsText())
        } }
    }

    @Test fun `concurrent TLS failures and cancellation cannot poison later business requests`() = runBlocking<Unit> {
        HttpFixture().use { server -> BrokenTlsFixture().use { tls -> withClient { client ->
            val failures = withTimeout(20_000) {
                coroutineScope {
                    List(96) { index -> async {
                        try {
                            withTimeout(if (index % 2 == 0) 30 else 300) { client.get(tls.url).bodyAsText() }
                            error("invalid TLS unexpectedly accepted")
                        } catch (error: Exception) { error }
                    } }.awaitAll()
                }
            }
            assertTrue(failures.none { it.message == "invalid TLS unexpectedly accepted" })
            assertTrue(failures.none { error -> generateSequence<Throwable>(error) { it.cause }
                .any { it.message.orEmpty().contains("number of released permits") } })
            assertEquals("ok", client.get(server.url + "/ok").bodyAsText())
        } } }
    }

    private suspend fun <T> withClient(retries: Int = 0, block: suspend (HttpClient) -> T): T =
        RequestManager.createHttpClient().use { original ->
            original.config {
                install(HttpRequestRetry) { maxRetries = retries; delayMillis { 0 } }
                install(HttpTimeout) { requestTimeoutMillis = 2_000; connectTimeoutMillis = 1_000; socketTimeoutMillis = 2_000 }
            }.use { block(it) }
        }

    @Serializable private data class Payload(val value: String)

    private class HttpFixture : AutoCloseable {
        private val executor = Executors.newFixedThreadPool(8) { task -> Thread(task, "http-transport-fixture").apply { isDaemon = true } }
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val url = "http://127.0.0.1:${server.address.port}"
        val body = AtomicReference("")
        val userAgent = AtomicReference("")
        val retries = AtomicInteger()
        init {
            server.executor = executor
            server.createContext("/") { exchange ->
                try {
                    val path = exchange.requestURI.path
                    body.set(exchange.requestBody.use { it.readAllBytes().toString(Charsets.UTF_8) })
                    userAgent.set(exchange.requestHeaders.getFirst("User-Agent").orEmpty())
                    if (path == "/slow") Thread.sleep(300)
                    val retry = path == "/retry" && retries.incrementAndGet() == 1
                    val response = (if (path == "/echo") """{"value":"中文と한국어","ignored":true}""" else "ok").toByteArray()
                    exchange.responseHeaders.set("Cache-Control", "no-store")
                    if (path == "/echo") exchange.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
                    exchange.sendResponseHeaders(if (retry) 503 else 200, response.size.toLong())
                    exchange.responseBody.use { it.write(response) }
                } catch (_: IOException) { /* caller may time out before the response */
                } catch (_: InterruptedException) { Thread.currentThread().interrupt()
                } finally { exchange.close() }
            }
            server.start()
        }
        override fun close() { server.stop(0); executor.shutdownNow() }
    }

    /** Deliberately sends plain HTTP to a TLS ClientHello; never disables certificate validation. */
    private class BrokenTlsFixture : AutoCloseable {
        private val server = ServerSocket(0, 128, InetAddress.getByName("127.0.0.1"))
        private val sockets = ConcurrentHashMap.newKeySet<Socket>()
        private val pool = Executors.newFixedThreadPool(8) { task -> Thread(task, "broken-tls-fixture").apply { isDaemon = true } }
        val url = "https://127.0.0.1:${server.localPort}/fixture"
        private val acceptor = Thread {
            while (!server.isClosed) {
                val socket = try { server.accept() } catch (_: IOException) { break }
                sockets += socket
                pool.submit {
                    try {
                        socket.soTimeout = 300
                        socket.getInputStream().read()
                        socket.getOutputStream().write("HTTP/1.1 400 Not TLS\r\n\r\n".toByteArray())
                    } catch (_: IOException) { }
                    finally { socket.close(); sockets -= socket }
                }
            }
        }.apply { isDaemon = true; start() }
        override fun close() {
            server.close(); sockets.forEach { runCatching { it.close() } }; pool.shutdownNow(); acceptor.join(1_000)
        }
    }
}
