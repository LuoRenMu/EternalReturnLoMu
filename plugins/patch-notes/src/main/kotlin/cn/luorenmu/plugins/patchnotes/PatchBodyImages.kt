package cn.luorenmu.plugins.patchnotes

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Duration
import javax.imageio.ImageIO

@Serializable
data class PatchBodyImage(val sourceUrl: String, val caption: String, val fileName: String, val width: Int, val height: Int)

/** Official article-body photos only: no list thumbnails, navigation, skill icons, or animated GIFs. */
class PatchBodyImages(
    private val select: suspend (PatchSummary, List<PatchImageCandidate>) -> List<Int>? = { _, _ -> null },
    private val download: suspend (String) -> ByteArray = { downloadOfficialImage(it) },
) {
    private data class Candidate(val url: String, val heading: String, val context: String)

    internal fun candidates(html: String, articleUrl: String): List<Pair<String, String>> =
        extract(html, articleUrl).map { it.url to it.heading }

    private fun extract(html: String, articleUrl: String): List<Candidate> {
        val doc = Jsoup.parse(html)
        val body = doc.selectFirst(".er-article-detail__content.er-article-content.fr-view")
            ?: doc.selectFirst(".er-article-content,article") ?: return emptyList()
        body.select("script,style,nav,footer").remove()
        var heading = "官方正文配图"
        return buildList {
            body.select("h1,h2,h3,h4,h5,h6,img").forEach { element ->
                if (element.tagName() != "img") heading = element.text().take(50).ifBlank { heading }
                else {
                    val raw = element.attr("data-src").ifBlank { element.attr("src") }
                    val url = runCatching { URI(articleUrl).resolve(raw) }.getOrNull() ?: return@forEach
                    if (!isAllowedImage(url)) return@forEach
                    val declaredWidth = element.attr("width").removeSuffix("px").toIntOrNull()
                    val declaredHeight = element.attr("height").removeSuffix("px").toIntOrNull()
                    if (declaredWidth != null && declaredWidth < 240 || declaredHeight != null && declaredHeight < 120) return@forEach
                    val anchor: org.jsoup.nodes.Element = element.parent()?.takeIf { it != body } ?: element
                    val surrounding = listOfNotNull(element.attr("alt"),
                        anchor.previousElementSibling()?.text(),
                        anchor.nextElementSibling()?.text()).joinToString(" ").take(500)
                    add(Candidate(url.toASCIIString(), heading, surrounding))
                }
            }
        }.distinctBy { it.url }.take(80)
    }

    suspend fun collect(html: String, articleUrl: String, articleDirectory: Path, summary: PatchSummary? = null): List<PatchBodyImage> = withContext(Dispatchers.IO) {
        val pool = extract(html, articleUrl)
        val chosen = if (summary == null || pool.isEmpty()) null else try {
            select(summary, pool.mapIndexed { index, candidate -> PatchImageCandidate(index, candidate.heading, candidate.context) })
                ?.takeIf { ids -> ids.size <= 3 && ids.distinct().size == ids.size && ids.all { it in pool.indices } }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
        // Round-robin chapters when AI is unavailable; don't fill the gallery with one character's consecutive images.
        val groups = pool.indices.groupBy { pool[it].heading }.values.toList()
        val fallback = (0 until (groups.maxOfOrNull { it.size } ?: 0)).flatMap { depth -> groups.mapNotNull { it.getOrNull(depth) } }
        val ordered = ((chosen ?: emptyList()) + fallback).distinct().map { pool[it] }
        val selected = mutableListOf<PatchBodyImage>()
        var failures = 0
        for ((url, caption) in ordered.take(16)) {
            if (selected.size == 3) break
            currentCoroutineContext().ensureActive()
            val name = MessageDigest.getInstance("SHA-256").digest(url.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }.take(20)
            val relative = "media/$name.png"
            val target = articleDirectory.resolve(relative)
            try {
                val bytes = if (Files.isRegularFile(target)) Files.readAllBytes(target) else download(url)
                require(bytes.size in 32..8_000_000)
                ImageIO.createImageInputStream(bytes.inputStream()).use { stream ->
                    val readers = ImageIO.getImageReaders(stream)
                    check(readers.hasNext()) { "正文配图不是可解码的图片" }
                    val reader = readers.next()
                    try {
                        reader.input = stream
                        val width = reader.getWidth(0)
                        val height = reader.getHeight(0)
                        if (width < 240 || height < 120 || width > 6000 || height > 6000 || width.toLong() * height > 20_000_000) return@use
                        val image = reader.read(0)
                        Files.createDirectories(target.parent)
                        val temporary = Files.createTempFile(target.parent, ".photo-", ".png")
                        try {
                            check(ImageIO.write(image, "png", temporary.toFile()))
                            currentCoroutineContext().ensureActive()
                            try {
                                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
                            }
                        } finally {
                            Files.deleteIfExists(temporary)
                        }
                        selected += PatchBodyImage(url, caption, relative, width, height)
                    } finally {
                        reader.dispose()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                failures++
                Files.deleteIfExists(target)
            }
        }
        // A saved textual summary remains queryable, but a failed media scan must retry later.
        check(failures == 0 || selected.size == 3) { "部分正文配图获取失败，下轮重试" }
        selected
    }

    companion object {
        private val httpClient by lazy {
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build()
        }
        private fun isAllowedImage(uri: URI) = uri.scheme == "https" && uri.host == "cdn.playeternalreturn.com" &&
            uri.port in listOf(-1, 443) && uri.userInfo == null && uri.path.substringAfterLast('.').lowercase() in setOf("png", "jpg", "jpeg")

        private suspend fun downloadOfficialImage(url: String): ByteArray = withContext(Dispatchers.IO) {
            require(isAllowedImage(URI(url)))
            val request = HttpRequest.newBuilder(URI(url)).timeout(Duration.ofSeconds(20)).GET().build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream())
            response.body().use { input ->
                check(response.statusCode() == 200)
                val bytes = input.readNBytes(8_000_001)
                require(bytes.size <= 8_000_000)
                bytes
            }
        }
    }
}
