package cn.luorenmu.plugins.bililive

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import love.forte.simbot.message.Message
import love.forte.simbot.message.OfflineImage.Companion.toOfflineImage
import love.forte.simbot.message.messagesOf
import love.forte.simbot.message.toText
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal enum class BiliLiveEvent { STARTED, ENDED }

internal data class BiliLiveNotice(val room: BiliRoom, val timestamp: Long, val event: BiliLiveEvent = BiliLiveEvent.STARTED) {
    val text: String get() = (if (event == BiliLiveEvent.ENDED) "已下播喵~" else "已开播！") +
        "\nUP主：${livePlainText(room.upName).ifBlank { "未知UP主" }}" +
        "\n直播标题：${livePlainText(room.title).ifBlank { "暂无标题" }}" +
        "\n${if (event == BiliLiveEvent.ENDED) "直播时长" else "已开播"}：${liveElapsed(room.session, timestamp)}\n直播间：${room.id}"
}

internal fun liveElapsed(session: String, now: Long): String {
    val text = session.trim()
    val started = runCatching { Instant.parse(text) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(text).toInstant() }.getOrNull()
        ?: runCatching { LocalDateTime.parse(text, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            .atZone(ZoneId.of("Asia/Shanghai")).toInstant() }.getOrNull()
        ?: return "未知"
    if (started.toEpochMilli() > now) return "未知"
    val minutes = Duration.between(started, Instant.ofEpochMilli(now)).toMinutes()
    return if (minutes < 60) "${minutes}分钟" else "${minutes / 60}小时 ${minutes % 60}分钟"
}

internal fun livePlainText(value: String): String = value.replace(Regex("(?i)(?:https?://|www\\.)\\S+"), "")
    .replace(Regex("\\s+"), " ").trim()

/** A cover is downloaded as bytes and uploaded by Simbot, never shown as a HTTP link in the chat. */
internal class BiliLiveMessages(
    private val fetchCover: suspend (URI) -> ByteArray = ::loadBiliCover,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private data class Cached(val bytes: ByteArray?, val expiresAt: Long)
    private val cache = LinkedHashMap<URI, Cached>(4, 0.75f, true)
    private val mutex = Mutex()
    private val log = KotlinLogging.logger {}

    suspend fun build(notice: BiliLiveNotice): Message {
        val uri = biliCoverUri(notice.room.coverUrl)
        val bytes = if (uri == null) null else mutex.withLock {
            val cached = cache[uri]
            if (cached != null && cached.expiresAt > now()) return@withLock cached.bytes
            val loaded = try {
                fetchCover(uri).also(::validateBiliCover)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (error: Exception) {
                log.warn { "B站直播封面获取失败：${error.javaClass.simpleName}" }
                null
            }
            cache[uri] = Cached(loaded, now() + 60_000)
            while (cache.size > 4) cache.remove(cache.keys.first())
            loaded
        }
        return if (bytes == null) (notice.text + "\n封面暂不可用").toText()
        else messagesOf(bytes.toOfflineImage(), notice.text.toText())
    }
}

internal fun biliCoverUri(value: String): URI? = runCatching {
    require(value.length <= 2048)
    val uri = URI(value)
    val host = uri.host?.lowercase(Locale.ROOT) ?: return null
    require(host.endsWith(".hdslb.com") && uri.scheme in listOf("http", "https"))
    require(uri.userInfo == null && uri.fragment == null && uri.port in listOf(-1, 80, 443))
    URI("https", null, host, -1, uri.path, uri.query, null)
}.getOrNull()

private val coverClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
    .followRedirects(HttpClient.Redirect.NEVER).build()

private suspend fun loadBiliCover(uri: URI): ByteArray = runInterruptible(Dispatchers.IO) {
    val request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(8))
        .header("User-Agent", "Mozilla/5.0").header("Referer", "https://live.bilibili.com/").GET().build()
    val response = coverClient.send(request, HttpResponse.BodyHandlers.ofInputStream())
    response.body().use { body ->
        check(response.statusCode() == 200) { "BiliCoverHttp${response.statusCode()}" }
        check(response.headers().firstValue("Content-Type").orElse("").startsWith("image/", true)) { "BiliCoverNotImage" }
        body.readNBytes(MAX_COVER_BYTES + 1).also(::validateBiliCover)
    }
}

private const val MAX_COVER_BYTES = 4 * 1024 * 1024

internal fun validateBiliCover(bytes: ByteArray) {
    require(bytes.size in 12..MAX_COVER_BYTES) { "BiliCoverSize" }
    val png = bytes.take(8) == listOf(137.toByte(), 80.toByte(), 78.toByte(), 71.toByte(), 13.toByte(), 10.toByte(), 26.toByte(), 10.toByte())
    val jpeg = bytes[0] == 255.toByte() && bytes[1] == 216.toByte() && bytes[2] == 255.toByte()
    val gif = String(bytes, 0, 6, Charsets.US_ASCII) in listOf("GIF87a", "GIF89a")
    val webp = String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" && String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP"
    require(png || jpeg || gif || webp) { "BiliCoverInvalidImage" }
}
