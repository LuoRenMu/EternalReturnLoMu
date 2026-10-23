package cn.luorenmu.plugins.qqmedia

import cn.luorenmu.exception.MessageReplyException
import love.forte.simbot.message.Image
import love.forte.simbot.message.OfflineImage
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.net.URI
import java.security.MessageDigest
import kotlinx.serialization.json.*

data class PublishedQQImage(val url: String, val width: Int, val height: Int)

interface QQRichMediaTransport {
    suspend fun post(path: String, body: JsonObject): JsonObject
    fun put(url: URI, bytes: ByteArray)
    fun verify(url: URI, bytes: ByteArray)
}

/** Upload the original PNG to QQ; only a completed chunk upload returns a usable raw_url. */
object QQImagePublisher {
    private const val MAX_IMAGE_BYTES = 15 * 1024 * 1024

    suspend fun publish(
        image: Image,
        groupId: String,
        transport: QQRichMediaTransport = QQRichMediaSimbotTransport(),
    ): PublishedQQImage {
        if (!groupId.matches(Regex("[A-Fa-f0-9]{32}"))) {
            throw MessageReplyException("请在 QQ 群中使用此命令；后台测试需要填写真实 QQ groupId")
        }
        val bytes = (image as? OfflineImage)?.data()
            ?: throw MessageReplyException("玩家查询没有返回本地战绩图片，无法生成 Markdown 测试消息")
        require(bytes.size in 29..MAX_IMAGE_BYTES) { "Invalid player image size" }
        val dimensions = DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            val signature = ByteArray(8).also(input::readFully)
            require(signature.contentEquals(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10))) { "Player image is not PNG" }
            require(input.readInt() == 13 && input.readInt() == 0x49484452) { "Invalid PNG header" }
            val width = input.readInt()
            val height = input.readInt()
            require(width > 0 && height > 0) { "Invalid PNG dimensions" }
            width to height
        }
        val base = "/v2/groups/$groupId"
        val prepared = transport.post("$base/upload_prepare", buildJsonObject {
            put("file_type", 1)
            put("file_size", bytes.size.toString())
            put("file_name", "player.png")
            put("md5", digest("MD5", bytes))
            put("sha1", digest("SHA-1", bytes))
            // Official md5_10m boundary is 10002432 bytes, not 10 * 1024 * 1024.
            put("md5_10m", digest("MD5", bytes.copyOfRange(0, minOf(bytes.size, 10_002_432))))
        })
        val uploadId = prepared["upload_id"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
            ?: throw MessageReplyException("QQ 图片预上传未返回 upload_id")
        val parts = prepared["parts"]?.jsonArray?.map { it.jsonObject }
            ?.sortedBy { it["index"]?.jsonPrimitive?.intOrNull ?: -1 }
            ?: throw MessageReplyException("QQ 图片预上传未返回分片信息")
        require(parts.size in 1..64) { "Invalid QQ upload part count" }
        // Validate the full part plan before any PUT, rather than completing a partially invalid upload.
        var planned = 0
        val chunks = parts.mapIndexed { index, part ->
            // QQ/COS numbers parts from 1; zero-based local array indexes must not reach the API.
            require(part["index"]?.jsonPrimitive?.intOrNull == index + 1) { "Invalid QQ upload part indexes" }
            val block = part["block_size"]?.jsonPrimitive?.content?.toIntOrNull()
                ?: prepared["block_size"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            require(block > 0 && planned < bytes.size) { "Invalid QQ upload block size" }
            val size = minOf(block, bytes.size - planned)
            val url = downloadUri(part["presigned_url"]?.jsonPrimitive?.contentOrNull)
            Triple(url, planned, size).also { planned += size }
        }
        require(planned == bytes.size) { "Incomplete QQ upload part plan" }
        chunks.forEachIndexed { index, (url, offset, size) ->
            val chunk = bytes.copyOfRange(offset, offset + size)
            transport.put(url, chunk)
            transport.post("$base/upload_part_finish", buildJsonObject {
                put("upload_id", uploadId)
                put("part_index", index + 1)
                put("block_size", chunk.size.toString())
                put("md5", digest("MD5", chunk))
            })
        }
        val completed = transport.post("$base/files", buildJsonObject {
            put("file_type", 1)
            put("srv_send_msg", false) // Upload only; the final Markdown reply is the only message sent.
            put("file_name", "player.png")
            put("upload_id", uploadId)
        })
        // file_info is an opaque media token, not a URL. Never substitute it or fall back to FRP.
        val url = downloadUri(completed["raw_url"]?.jsonPrimitive?.contentOrNull)
        transport.verify(url, bytes)
        return PublishedQQImage(url.toASCIIString(), dimensions.first, dimensions.second)
    }

    private fun digest(algorithm: String, bytes: ByteArray): String =
        MessageDigest.getInstance(algorithm).digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun downloadUri(value: String?): URI {
        val url = value?.takeIf(String::isNotBlank)
            ?: throw MessageReplyException("QQ 官方图片上传未返回下载链接（raw_url），不能发送 Markdown 图片")
        val uri = runCatching { URI(url) }.getOrNull()
        if (uri == null || uri.scheme != "https" || uri.host.isNullOrBlank() || uri.userInfo != null ||
            uri.fragment != null || url.any { it in "\r\n()[]" }) {
            throw MessageReplyException("QQ 官方图片接口返回了无效的下载地址")
        }
        return uri
    }

    internal fun verifyResponse(status: Int, contentType: String, received: ByteArray, expected: ByteArray) {
        if (status != 200 || contentType.substringBefore(';').trim().lowercase() !in setOf("image/png", "application/octet-stream") ||
            !received.contentEquals(expected)) {
            throw MessageReplyException("QQ 官方图片读取失败（HTTP $status 或图片不完整），本次不发送图片按钮消息")
        }
    }
}
