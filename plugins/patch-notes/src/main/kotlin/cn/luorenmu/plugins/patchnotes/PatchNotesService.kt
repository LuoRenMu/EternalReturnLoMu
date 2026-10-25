package cn.luorenmu.plugins.patchnotes

import cn.luorenmu.nutdraw.NutDraw
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import javax.imageio.ImageIO

@Serializable
data class PatchArticle(val id: Int, val title: String, val createdAt: String, val updatedAt: String, val url: String,
    val language: String = "zh-CN")

@Serializable
data class PatchNoteRecord(val article: PatchArticle, val summary: PatchSummary,
    val images: List<PatchBodyImage> = emptyList(), val bodyImagesVersion: Int = 0,
    val summaryVersion: Int = 0)

@Serializable
private data class PatchImageRecord(val digest: String, val summaryDigest: String, val rendererVersion: Int)

/** One article directory is the persistent cache. Failed scans are not marked as processed. */
class PatchNotesService(
    private val directory: Path,
    private val articles: suspend () -> List<PatchArticle>,
    private val content: suspend (PatchArticle) -> String,
    private val summarize: suspend (String, String) -> PatchSummary?,
    private val bodyImages: PatchBodyImages = PatchBodyImages(),
    private val render: suspend (PatchNoteRecord, Path) -> Unit = { record, target ->
        NutDraw.render(PatchNotesTemplate(target.parent), record, target)
    },
) {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    @Volatile private var latestArticle: PatchArticle? = null

    /** Select the newest article BEFORE checking its cache; never backfill older announcements. */
    suspend fun scan(): Int = mutex.withLock {
        withContext(Dispatchers.IO) {
            Files.createDirectories(directory)
            var processed = 0
            val pending = articles().sortedWith(compareByDescending<PatchArticle> { it.createdAt }.thenByDescending { it.id })
                .take(1).also { latestArticle = it.firstOrNull() }
                .filter { article ->
                    val cached = readRecord(article.id)
                    cached == null || cached.article != article || cached.summaryVersion != SUMMARY_VERSION ||
                        cached.bodyImagesVersion != 2 || !validImage(cached)
                }
            var failed = 0
            for (article in pending) {
                try {
                    require(article.id > 0)
                    val old = readRecord(article.id)
                    var html: String? = null
                    val record = if (old?.article == article && old.summaryVersion == SUMMARY_VERSION) old else {
                        val body = content(article)
                        html = body
                        val summary = summarize(article.title, body) ?: error("AI摘要未生成")
                        currentCoroutineContext().ensureActive()
                        // A summary-format upgrade must not redownload already validated body images.
                        val reusable = old?.takeIf { it.article == article }
                        PatchNoteRecord(article, summary.validate(), reusable?.images.orEmpty(),
                            reusable?.bodyImagesVersion ?: 0, summaryVersion = SUMMARY_VERSION).also {
                            // Persist successful AI work before rendering so image failure doesn't spend tokens again.
                            atomicWrite(recordPath(article.id), json.encodeToString(it))
                        }
                    }
                    val illustrated = if (record.bodyImagesVersion == 2) record else {
                        val images = bodyImages.collect(html ?: content(article), article.url, recordPath(article.id).parent, record.summary)
                        record.copy(images = images, bodyImagesVersion = 2).also {
                            atomicWrite(recordPath(article.id), json.encodeToString(it))
                        }
                    }
                    ensureImage(illustrated)
                    processed++
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    failed++
                }
            }
            check(failed == 0) { "最新更新有 $failed 篇处理失败，下轮重试（本次完成 $processed 篇）" }
            processed
        }
    }

    /** Queries never queue behind AI work or substitute an older article for a pending newest one. */
    suspend fun latestImage(): Path? = withContext(Dispatchers.IO) {
        val latest = latestArticle
        val record = if (latest != null) readRecord(latest.id)?.takeIf { it.article == latest }
            else records().maxWithOrNull(compareBy<PatchNoteRecord> { it.article.createdAt }.thenBy { it.article.id })
        record?.takeIf { it.summaryVersion == SUMMARY_VERSION && validImage(it) }?.let { imagePath(it.article.id) }
    }

    suspend fun image(articleId: Int? = null): Path? {
        // Already validated cached images should not queue behind a long-running LLM scan.
        val ready = withContext(Dispatchers.IO) {
            val record = if (articleId != null) readRecord(articleId) else records()
                .maxWithOrNull(compareBy<PatchNoteRecord> { it.article.createdAt }.thenBy { it.article.id })
            record?.takeIf { validImage(it) }?.let { imagePath(it.article.id) }
        }
        if (ready != null) return ready
        return mutex.withLock {
            withContext(Dispatchers.IO) {
                val record = if (articleId != null) readRecord(articleId) else records()
                    .maxWithOrNull(compareBy<PatchNoteRecord> { it.article.createdAt }.thenBy { it.article.id })
                record ?: return@withContext null
                ensureImage(record)
                imagePath(record.article.id)
            }
        }
    }

    private fun records(): List<PatchNoteRecord> {
        if (!Files.isDirectory(directory)) return emptyList()
        return Files.list(directory).use { stream ->
            stream.filter { Files.isDirectory(it) && it.fileName.toString().toIntOrNull()?.let { id -> id > 0 } == true }
                .map { readRecord(it.fileName.toString().toInt()) }.toList().filterNotNull()
        }
    }

    private fun readRecord(id: Int): PatchNoteRecord? {
        if (id <= 0) return null
        return runCatching {
            json.decodeFromString<PatchNoteRecord>(Files.readString(recordPath(id))).also {
                require(it.article.id == id)
                it.summary.validate()
            }
        }.getOrNull()
    }

    private fun validImage(record: PatchNoteRecord): Boolean = runCatching {
        val metadata = json.decodeFromString<PatchImageRecord>(Files.readString(imageRecordPath(record.article.id)))
        metadata.rendererVersion == 4 && metadata.summaryDigest == summaryDigest(record) &&
            metadata.digest == digest(imagePath(record.article.id)) && validPng(imagePath(record.article.id))
    }.getOrDefault(false)

    private suspend fun ensureImage(record: PatchNoteRecord) {
        if (validImage(record)) return
        val target = imagePath(record.article.id)
        Files.createDirectories(target.parent)
        val temporary = Files.createTempFile(target.parent, ".render-", ".png")
        try {
            render(record, temporary)
            currentCoroutineContext().ensureActive()
            check(validPng(temporary)) { "摘要图片完整性检查失败" }
            move(temporary, target)
            atomicWrite(imageRecordPath(record.article.id), json.encodeToString(
                PatchImageRecord(digest(target), summaryDigest(record), rendererVersion = 4)))
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun validPng(path: Path): Boolean = runCatching {
        require(Files.size(path) in 32..25_000_000)
        Files.newInputStream(path).use { input ->
            require(input.readNBytes(8).contentEquals(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)))
        }
        ImageIO.read(path.toFile())?.let { it.width > 0 && it.height > 0 } == true
    }.getOrDefault(false)

    private fun summaryDigest(record: PatchNoteRecord) = hash(json.encodeToString(record).toByteArray(Charsets.UTF_8))

    companion object {
        /** Old summaries may omit character names; reanalyze only the latest article once. */
        internal const val SUMMARY_VERSION = 1
    }
    private fun digest(path: Path) = hash(Files.readAllBytes(path))
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun recordPath(id: Int) = directory.resolve(id.toString()).resolve("summary.json")
    private fun imagePath(id: Int) = directory.resolve(id.toString()).resolve("summary.png")
    private fun imageRecordPath(id: Int) = directory.resolve(id.toString()).resolve("image.json")
    private fun atomicWrite(path: Path, text: String) {
        Files.createDirectories(path.parent)
        val temp = Files.createTempFile(path.parent, ".write-", ".json")
        try {
            Files.writeString(temp, text)
            move(temp, path)
        } finally {
            Files.deleteIfExists(temp)
        }
    }
    private fun move(source: Path, target: Path) {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
