package cn.luorenmu.plugins.qqmedia

import cn.luorenmu.exception.MessageReplyException
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*
import love.forte.simbot.qguild.api.PostQQGuildApi
import love.forte.simbot.qguild.api.QQGuildApi
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** QQ API calls use the existing Simbot bot and its authentication, never a separate access token. */
internal class QQRichMediaSimbotTransport(
    private val executeApi: suspend (QQGuildApi<JsonObject>) -> JsonObject = { SimbotQQRuntime.requireBot().executeData(it) },
) : QQRichMediaTransport {
    private companion object {
        val cosClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build()

    }

    override suspend fun post(path: String, body: JsonObject): JsonObject {
        require(Regex("/v2/groups/[A-Fa-f0-9]{32}/(upload_prepare|upload_part_finish|files)").matches(path))
        val apiPath = path.removePrefix("/").split('/').toTypedArray()
        val apiBody = TextContent(body.toString(), ContentType.Application.Json)
        val api = object : PostQQGuildApi<JsonObject>() {
            override val path = apiPath
            override fun createBody(): Any = apiBody
            override val resultDeserializationStrategy = JsonObject.serializer()
        }
        val result = try {
            executeApi(api)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // SDK errors may contain signed URLs or bodies; never expose those to a chat or logger.
            throw MessageReplyException("QQ 官方图片上传失败（${path.substringAfterLast('/')}，Simbot 请求失败）")
        }
        val code = result["err_code"]?.jsonPrimitive?.intOrNull ?: result["code"]?.jsonPrimitive?.intOrNull ?: 0
        if (code != 0) throw MessageReplyException("QQ 官方图片上传失败（${path.substringAfterLast('/')}，错误码 $code）")
        return result
    }

    override fun put(url: URI, bytes: ByteArray) {
        // The signed COS URL is NOT a QQ API. Never execute it through Simbot or attach bot credentials.
        val request = HttpRequest.newBuilder(url).timeout(Duration.ofSeconds(45))
            .header("Content-Type", "image/png").PUT(HttpRequest.BodyPublishers.ofByteArray(bytes)).build()
        val response = cosClient.send(request, HttpResponse.BodyHandlers.discarding())
        if (response.statusCode() !in 200..299) throw MessageReplyException("QQ 官方图片分片上传失败（HTTP ${response.statusCode()}）")
    }

    override fun verify(url: URI, bytes: ByteArray) {
        val request = HttpRequest.newBuilder(url).timeout(Duration.ofSeconds(20)).GET().build()
        val response = cosClient.send(request, HttpResponse.BodyHandlers.ofInputStream())
        response.body().use { body ->
            QQImagePublisher.verifyResponse(response.statusCode(), response.headers().firstValue("Content-Type").orElse(""),
                body.readNBytes(bytes.size + 1), bytes)
        }
    }
}
