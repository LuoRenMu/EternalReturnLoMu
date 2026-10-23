package cn.luorenmu.plugins.test

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.serialization.json.*
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

internal data class BiliRoom(val id: Long, val title: String, val status: Int, val session: String)

internal class BiliLiveSource {
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build()
    suspend fun fetch(id: Long): BiliRoom = runInterruptible(Dispatchers.IO) {
        require(id > 0)
        val request = HttpRequest.newBuilder(URI("https://api.live.bilibili.com/room/v1/Room/get_info?room_id=$id"))
            .timeout(Duration.ofSeconds(12)).header("User-Agent", "Mozilla/5.0")
            .header("Referer", "https://live.bilibili.com/").GET().build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
        response.body().use { body ->
            check(response.statusCode() == 200) { "BiliHttp${response.statusCode()}" }
            val bytes = body.readNBytes(262_145)
            check(bytes.size <= 262_144) { "BiliResponseTooLarge" }
            parse(bytes.toString(Charsets.UTF_8))
        }
    }

    internal fun parse(text: String): BiliRoom {
        val root = Json.parseToJsonElement(text).jsonObject
        check(root["code"]?.jsonPrimitive?.int == 0) { "BiliApiFailure" }
        val data = root.getValue("data").jsonObject
        val id = data.getValue("room_id").jsonPrimitive.long
        val status = data.getValue("live_status").jsonPrimitive.int
        require(id > 0 && status in 0..2) { "BiliInvalidRoom" }
        return BiliRoom(id, data["title"]?.jsonPrimitive?.content.orEmpty().take(150), status,
            data["live_time"]?.jsonPrimitive?.content.orEmpty())
    }
}

internal fun biliRoomId(value: String): Long? {
    val text = value.trim()
    val digits = if (text.matches(Regex("[0-9]+"))) text else {
        val uri = runCatching { URI(text) }.getOrNull() ?: return null
        if (uri.scheme !in listOf("https", "http") || uri.host != "live.bilibili.com" || uri.userInfo != null) return null
        Regex("/(?:blanc/)?([0-9]+)/?").matchEntire(uri.path)?.groupValues?.get(1) ?: return null
    }
    return digits.toLongOrNull()?.takeIf { it > 0 }
}
