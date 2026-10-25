package cn.luorenmu.nutdraw

import cn.luorenmu.nutdraw.render.HttpImageSource
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import kotlin.test.Test
import kotlin.test.assertFailsWith

class HttpImageSourceTest {
    @Test
    fun `remote image byte limit rejects an oversized response`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/large") { exchange ->
            exchange.sendResponseHeaders(200, 32)
            exchange.responseBody.use { it.write(ByteArray(32)) }
        }
        server.start()
        try {
            assertFailsWith<IllegalArgumentException> {
                HttpImageSource(maxBytes = 16).load("http://127.0.0.1:${server.address.port}/large")
            }
        } finally { server.stop(0) }
    }

    @Test
    fun `stalled remote image times out instead of blocking the render indefinitely`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/slow") { exchange ->
            exchange.sendResponseHeaders(200, 1)
            Thread.sleep(400)
            runCatching { exchange.responseBody.use { it.write(byteArrayOf(1)) } }
        }
        server.start()
        try {
            assertFailsWith<SocketTimeoutException> {
                HttpImageSource(readTimeoutMillis = 100).load("http://127.0.0.1:${server.address.port}/slow")
            }
        } finally { server.stop(0) }
    }
}
