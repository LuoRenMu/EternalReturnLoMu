package cn.luorenmu.plugins.qqmedia

import cn.luorenmu.exception.MessageReplyException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import love.forte.simbot.message.OfflineImage
import org.junit.jupiter.api.io.TempDir
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Base64
import java.util.HexFormat
import kotlin.test.*

class QQImagePublisherTest {
    @TempDir lateinit var root: Path
    private val png = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a0XcAAAAASUVORK5CYII=")
    private val group = "A".repeat(32)
    private val rawUrl = "https://qqbot-file-upload-1251316161.cos.accelerate.myqcloud.com/test.png?sign=fixture&expires=86400"

    private fun image(): OfflineImage {
        val source = root.resolve("백수.png")
        Files.write(source, png)
        return OfflineImage.fileOfflineImage(source.toString())
    }

    private fun hash(algorithm: String, data: ByteArray) = HexFormat.of().formatHex(MessageDigest.getInstance(algorithm).digest(data))

    private inner class FakeTransport : QQRichMediaTransport {
        val posts = mutableListOf<Pair<String, JsonObject>>()
        val chunks = mutableListOf<ByteArray>()
        var verified = false
        var failPut = false
        var missingRawUrl = false
        // Captured official upload_prepare response uses 1-based indexes.
        var partIndexOffset = 1
        override suspend fun post(path: String, body: JsonObject): JsonObject {
            posts += path to body
            return when (path.substringAfterLast('/')) {
                "upload_prepare" -> buildJsonObject {
                    put("upload_id", "fixture-upload")
                    put("block_size", "23")
                    putJsonArray("parts") {
                        // Deliberately reverse the plan; uploader must PUT/finish in index order.
                        for (index in 2 downTo 0) add(buildJsonObject {
                            put("index", index + partIndexOffset)
                            put("block_size", "23")
                            put("presigned_url", "https://cos.example/part-$index?sign=fixture")
                        })
                    }
                }
                "upload_part_finish" -> buildJsonObject { }
                "files" -> buildJsonObject {
                    put("file_info", "opaque-not-an-image-url")
                    put("ttl", 86400)
                    if (!missingRawUrl) put("raw_url", rawUrl)
                }
                else -> fail("Must not send a message or use undocumented endpoint: $path")
            }
        }
        override fun put(url: URI, bytes: ByteArray) {
            assertEquals("/part-${chunks.size}", url.path)
            if (failPut) throw MessageReplyException("fixture PUT failed")
            chunks += bytes
        }
        override fun verify(url: URI, bytes: ByteArray) {
            assertEquals(rawUrl, url.toString())
            assertContentEquals(png, bytes)
            verified = true
        }
    }

    @Test fun `uploads all original bytes checksums completes without sending and uses official raw url`() = runBlocking<Unit> {
        val transport = FakeTransport()
        val result = QQImagePublisher.publish(image(), group, transport)
        assertEquals(rawUrl, result.url)
        assertEquals(1, result.width)
        assertEquals(1, result.height)
        assertTrue(transport.verified)
        assertContentEquals(png, transport.chunks.fold(byteArrayOf()) { all, chunk -> all + chunk })
        assertContentEquals(png, Files.readAllBytes(root.resolve("백수.png")))
        assertEquals(listOf("upload_prepare", "upload_part_finish", "upload_part_finish", "upload_part_finish", "files"),
            transport.posts.map { it.first.substringAfterLast('/') })
        assertTrue(transport.posts.all { it.first.startsWith("/v2/groups/$group/") })
        val prepared = transport.posts.first().second
        assertEquals(png.size.toString(), prepared["file_size"]?.jsonPrimitive?.content)
        assertTrue(prepared["file_size"]!!.jsonPrimitive.isString)
        assertEquals(hash("MD5", png), prepared["md5"]?.jsonPrimitive?.content)
        assertEquals(hash("SHA-1", png), prepared["sha1"]?.jsonPrimitive?.content)
        assertEquals(hash("MD5", png), prepared["md5_10m"]?.jsonPrimitive?.content)
        transport.posts.filter { it.first.endsWith("upload_part_finish") }.forEachIndexed { index, (_, body) ->
            assertEquals(index + 1, body["part_index"]?.jsonPrimitive?.int)
            assertEquals(transport.chunks[index].size.toString(), body["block_size"]?.jsonPrimitive?.content)
            assertEquals(hash("MD5", transport.chunks[index]), body["md5"]?.jsonPrimitive?.content)
        }
        val complete = transport.posts.last().second
        assertEquals(false, complete["srv_send_msg"]?.jsonPrimitive?.boolean)
        assertEquals("fixture-upload", complete["upload_id"]?.jsonPrimitive?.content)
        assertNull(complete["url"], "Must use chunk upload, not upload an inaccessible FRP URL")
    }

    @Test fun `opaque file info cannot replace missing raw url and no FRP fallback is used`() = runBlocking<Unit> {
        val transport = FakeTransport().also { it.missingRawUrl = true }
        assertFailsWith<MessageReplyException> { QQImagePublisher.publish(image(), group, transport) }
        assertFalse(transport.verified)
    }

    @Test fun `failed PUT never confirms a part completes upload or sends anything`() = runBlocking<Unit> {
        val transport = FakeTransport().also { it.failPut = true }
        assertFailsWith<MessageReplyException> { QQImagePublisher.publish(image(), group, transport) }
        assertEquals(1, transport.posts.size)
        assertFalse(transport.verified)
    }

    @Test fun `invalid group and part plans fail before uploading`() = runBlocking<Unit> {
        val transport = FakeTransport()
        assertFailsWith<MessageReplyException> { QQImagePublisher.publish(image(), "web-admin-group", transport) }
        assertTrue(transport.posts.isEmpty())
        transport.partIndexOffset = 0
        assertFailsWith<IllegalArgumentException> { QQImagePublisher.publish(image(), group, transport) }
        assertTrue(transport.chunks.isEmpty())
    }

    @Test fun `COS binary mime is allowed only when complete bytes match and errors HTML redirects are rejected`() {
        QQImagePublisher.verifyResponse(200, "image/png", png, png)
        QQImagePublisher.verifyResponse(200, "application/octet-stream", png, png)
        for (status in listOf(301, 403, 404, 500)) {
            assertFailsWith<MessageReplyException> { QQImagePublisher.verifyResponse(status, "image/png", png, png) }
        }
        assertFailsWith<MessageReplyException> { QQImagePublisher.verifyResponse(200, "text/html", png, png) }
        assertFailsWith<MessageReplyException> { QQImagePublisher.verifyResponse(200, "image/png", png.dropLast(1).toByteArray(), png) }
        assertFailsWith<MessageReplyException> { QQImagePublisher.verifyResponse(200, "image/png", png + byteArrayOf(1), png) }
    }
}
