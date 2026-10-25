package cn.luorenmu.plugins.bililive

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.serialization.json.*
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import io.github.oshai.kotlinlogging.KotlinLogging

internal data class BiliRoom(
    val id: Long, val title: String, val status: Int, val session: String,
    val upName: String = "", val coverUrl: String = "", val uid: Long = 0,
)

internal class BiliLiveSource(
    private val load: (URI) -> String = ::loadBiliJson,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private data class Anchor(val name: String, val expiresAt: Long)
    private val anchors = ConcurrentHashMap<Pair<Long, Long>, Anchor>()
    private val log = KotlinLogging.logger {}

    suspend fun fetch(id: Long): BiliRoom = runInterruptible(Dispatchers.IO) {
        require(id > 0)
        val room = parse(load(URI("https://api.live.bilibili.com/room/v1/Room/get_info?room_id=$id")))
        val key = room.id to room.uid
        val cached = anchors[key]
        val anchor = if (cached != null && cached.expiresAt > now()) cached else {
            try {
                val data = envelope(load(URI("https://api.live.bilibili.com/live_user/v1/UserInfo/get_anchor_in_room?roomid=${room.id}")))
                val name = data.getValue("info").jsonObject.getValue("uname").jsonPrimitive.content.trim().take(100)
                check(name.isNotBlank()) { "BiliAnchorMissing" }
                Anchor(name, now() + 3_600_000)
            } catch (error: Exception) {
                if (error is InterruptedException || error is kotlinx.coroutines.CancellationException) throw error
                log.warn { "B站UP主名称查询失败：${error.javaClass.simpleName}" }
                Anchor(cached?.name.orEmpty(), now() + 60_000)
            }.also {
                if (anchors.size >= 128) anchors.entries.removeIf { entry -> entry.value.expiresAt <= now() }
                if (anchors.size >= 128) anchors.clear()
                anchors[key] = it
            }
        }
        room.copy(upName = anchor.name)
    }

    internal fun parse(text: String): BiliRoom {
        val data = envelope(text)
        val id = data.getValue("room_id").jsonPrimitive.long
        val status = data.getValue("live_status").jsonPrimitive.int
        require(id > 0 && status in 0..2) { "BiliInvalidRoom" }
        val cover = sequenceOf("user_cover", "keyframe").mapNotNull { data[it]?.jsonPrimitive?.contentOrNull }
            .firstOrNull { biliCoverUri(it) != null }.orEmpty()
        return BiliRoom(id, data["title"]?.jsonPrimitive?.contentOrNull.orEmpty().take(150), status,
            data["live_time"]?.jsonPrimitive?.contentOrNull.orEmpty(), coverUrl = cover,
            uid = data["uid"]?.jsonPrimitive?.longOrNull ?: 0)
    }

    private fun envelope(text: String): JsonObject {
        val root = Json.parseToJsonElement(text).jsonObject
        check(root["code"]?.jsonPrimitive?.int == 0) { "BiliApiFailure" }
        return root.getValue("data").jsonObject
    }
}

private val biliClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()

private fun loadBiliJson(uri: URI): String {
    val request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(8))
        .header("User-Agent", "Mozilla/5.0").header("Referer", "https://live.bilibili.com/").GET().build()
    val response = biliClient.send(request, HttpResponse.BodyHandlers.ofInputStream())
    return response.body().use { body ->
        check(response.statusCode() == 200) { "BiliHttp${response.statusCode()}" }
        val bytes = body.readNBytes(262_145)
        check(bytes.size <= 262_144) { "BiliResponseTooLarge" }
        bytes.toString(Charsets.UTF_8)
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
