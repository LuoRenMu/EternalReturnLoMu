package cn.luorenmu.request

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.runBlocking
import java.net.ServerSocket
import java.net.SocketException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals

class RequestManagerRetryTest {
    @Test
    fun `retries when server closes while response headers are incomplete`() = runBlocking {
        PrematureEofServer().use { server ->
            RequestManager.createHttpClient().use { client ->
                val response = client.get("${server.baseUrl}/test")

                assertEquals("ok", response.bodyAsText())
                assertEquals(2, server.requestCount.get())
            }
        }
    }

    @Test
    fun `retries the complete request when server closes during body receive`() = runBlocking {
        val attempts = AtomicInteger()

        val body = RequestManager.retryTransientRequest(maxRetries = 1) {
            if (attempts.incrementAndGet() == 1) {
                throw ClosedReceiveChannelException("Channel was closed")
            }
            "ok"
        }

        assertEquals("ok", body)
        assertEquals(2, attempts.get())
    }

    private class PrematureEofServer : AutoCloseable {
        private val socket = ServerSocket(0)
        val requestCount = AtomicInteger()
        val baseUrl = "http://127.0.0.1:${socket.localPort}"
        private val worker = thread(isDaemon = true, name = "premature-eof-server") {
            try {
                while (!socket.isClosed) {
                    socket.accept().use { connection ->
                        val attempt = requestCount.incrementAndGet()
                        val reader = connection.getInputStream().bufferedReader()
                        while (reader.readLine()?.isNotEmpty() == true) Unit
                        val response = if (attempt == 1) {
                            null
                        } else {
                            "HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nok"
                        }
                        if (response != null) {
                            connection.getOutputStream().apply {
                                write(response.toByteArray())
                                flush()
                            }
                        }
                    }
                }
            } catch (_: SocketException) {
                // Expected when the test closes the server socket.
            }
        }

        override fun close() {
            socket.close()
            worker.join(1_000)
        }
    }
}
