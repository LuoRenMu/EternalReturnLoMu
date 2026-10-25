package cn.luorenmu.plugins.patchnotes

import kotlinx.coroutines.async
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.*

class PatchNotesServiceTest {
    private val article = PatchArticle(3867, "测试更新公告", "2026-10-01T02:00:00Z", "2026-10-01T03:00:00Z", "https://playeternalreturn.com/posts/news/3867")
    private val summary = PatchSummary("测试摘要", listOf(PatchSection("系统", listOf("新增功能"))))
    private fun png(path: Path) { ImageIO.write(BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB), "png", path.toFile()) }

    @Test fun `restart reuses ID cached summary and validated image without AI or render`() = runBlocking {
        val root = Files.createTempDirectory("patch-cache-test")
        var calls = 0
        var renders = 0
        fun service() = PatchNotesService(root, { listOf(article) }, { "正文" }, { _, _ -> calls++; summary }, render = { _, path -> renders++; png(path) })
        assertEquals(1, service().scan())
        val first = service().image(3867)
        assertEquals(0, service().scan())
        assertEquals(first, service().image())
        assertEquals(1, calls)
        assertEquals(1, renders)
        assertTrue(Files.exists(root.resolve("3867/summary.json")))
        assertTrue(Files.exists(root.resolve("3867/image.json")))
    }

    @Test fun `corrupt PNG regenerates from persisted summary without another AI request`() = runBlocking {
        val root = Files.createTempDirectory("patch-corrupt-test")
        var calls = 0
        var renders = 0
        val service = PatchNotesService(root, { listOf(article) }, { "正文" }, { _, _ -> calls++; summary }, render = { _, path -> renders++; png(path) })
        service.scan()
        Files.writeString(assertNotNull(service.image()), "broken")
        service.image()
        assertEquals(1, calls)
        assertEquals(2, renders)
    }

    @Test fun `AI failure retries and image failure retains successful AI work`() = runBlocking {
        val root = Files.createTempDirectory("patch-retry-test")
        var calls = 0
        var renders = 0
        val service = PatchNotesService(root, { listOf(article) }, { "正文" }, { _, _ -> calls++; if (calls == 1) null else summary },
            render = { _, path -> renders++; if (renders == 1) error("render failed") else png(path) })
        assertFailsWith<IllegalStateException> { service.scan() }
        assertFailsWith<IllegalStateException> { service.scan() }
        assertEquals(1, service.scan())
        assertEquals(2, calls)
        assertEquals(2, renders)
    }

    @Test fun `revised article invalidates summary and image`() = runBlocking {
        val root = Files.createTempDirectory("patch-revision-test")
        var current = article
        var calls = 0
        val service = PatchNotesService(root, { listOf(current) }, { "正文" }, { _, _ -> calls++; summary }, render = { _, path -> png(path) })
        service.scan()
        current = article.copy(updatedAt = "2026-10-01T04:00:00Z")
        assertEquals(1, service.scan())
        assertEquals(2, calls)
    }

    @Test fun `old template images regenerate without requesting AI again`() = runBlocking {
        val root = Files.createTempDirectory("patch-template-upgrade")
        var calls = 0
        var renders = 0
        val service = PatchNotesService(root, { listOf(article) }, { "正文" }, { _, _ -> calls++; summary },
            render = { _, path -> renders++; png(path) })
        service.scan()
        val metadata = root.resolve("3867/image.json")
        Files.writeString(metadata, Files.readString(metadata).replace("\"rendererVersion\": 4", "\"rendererVersion\": 1"))
        assertEquals(1, service.scan())
        assertEquals(1, calls)
        assertEquals(2, renders)
    }

    @Test fun `simultaneous queries and scan share a single generation and unknown ID stays null`() = runBlocking {
        val root = Files.createTempDirectory("patch-parallel-test")
        var calls = 0
        val service = PatchNotesService(root, { listOf(article) }, { "正文" }, { _, _ -> calls++; summary }, render = { _, path -> png(path) })
        val a = async { service.scan() }
        val b = async { service.scan() }
        a.await(); b.await()
        assertEquals(1, calls)
        assertNull(service.image(-1))
        assertNull(service.image(12))
    }

    @Test fun `cached image query does not wait for another article AI generation`() = runBlocking {
        val root = Files.createTempDirectory("patch-fast-cache-test")
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var list = listOf(article)
        val service = PatchNotesService(root, { list }, { "正文" }, { title, _ ->
            if (title == "new") { started.complete(Unit); release.await() }
            summary
        }, render = { _, path -> png(path) })
        service.scan()
        list = list + article.copy(id = 4000, title = "new", createdAt = "2026-10-02T02:00:00Z")
        val scan = async { service.scan() }
        started.await()
        assertNotNull(withTimeout(1_000) { service.image(3867) })
        release.complete(Unit)
        scan.await()
        Unit
    }

    @Test fun `selection is persisted and old photo selection upgrades without redoing summary or downloads`() = runBlocking {
        val root = Files.createTempDirectory("patch-image-upgrade")
        var summaries = 0
        var selections = 0
        var downloads = 0
        val media = PatchBodyImages(select = { _, pool -> selections++; listOf(pool.last().id) }, download = {
            downloads++
            ByteArrayOutputStream().use { stream ->
                ImageIO.write(BufferedImage(640, 360, BufferedImage.TYPE_INT_RGB), "png", stream)
                stream.toByteArray()
            }
        })
        val service = PatchNotesService(root, { listOf(article) },
            { "<article><h3>系统调整</h3><img src='https://cdn.playeternalreturn.com/system.png'></article>" },
            { _, _ -> summaries++; summary }, bodyImages = media, render = { _, path -> png(path) })
        assertEquals(1, service.scan())
        assertEquals(0, service.scan())
        assertEquals(1, selections)
        val record = root.resolve("3867/summary.json")
        Files.writeString(record, Files.readString(record).replace("\"bodyImagesVersion\": 2", "\"bodyImagesVersion\": 1"))
        assertEquals(1, service.scan())
        assertEquals(1, summaries)
        assertEquals(2, selections)
        assertEquals(1, downloads)
        assertTrue(Files.readString(record).contains("\"bodyImagesVersion\": 2"))
    }

    @Test fun `legacy summary is hidden then regenerated once with character names without redownloading images`() = runBlocking {
        val root = Files.createTempDirectory("patch-summary-upgrade")
        val recordPath = root.resolve("3867/summary.json")
        val media = PatchBodyImage(sourceUrl = "https://cdn.playeternalreturn.com/test.png", caption = "角色调整",
            fileName = "media/12345678901234567890.png", width = 640, height = 360)
        Files.createDirectories(recordPath.parent)
        Files.writeString(recordPath, Json.encodeToString(PatchNoteRecord(article, summary, listOf(media), bodyImagesVersion = 2)))
        assertFalse(Files.readString(recordPath).contains("summaryVersion")) // Real pre-upgrade JSON has no version field.
        var calls = 0
        val named = PatchSummary("角色平衡", listOf(PatchSection("角色调整", listOf("妮琪：R基础伤害降低。"))))
        val service = PatchNotesService(root, { listOf(article) }, { "妮琪：R基础伤害降低。" }, { _, _ -> calls++; named },
            bodyImages = PatchBodyImages(download = { error("Must reuse existing body images") }),
            render = { record, path ->
                if (record.summaryVersion == PatchNotesService.SUMMARY_VERSION) {
                    assertEquals(named, record.summary)
                    assertEquals(listOf(media), record.images)
                }
                png(path)
            })
        service.image(3867) // Valid legacy image, not just a missing or corrupt PNG.
        assertNull(service.latestImage())
        assertEquals(1, service.scan())
        assertNotNull(service.latestImage())
        assertEquals(0, service.scan())
        assertEquals(1, calls)
        assertEquals(PatchNotesService.SUMMARY_VERSION, Json.decodeFromString<PatchNoteRecord>(Files.readString(recordPath)).summaryVersion)
    }

    @Test fun `summary migration failure preserves legacy cache and retries without serving unnamed summary`() = runBlocking {
        val root = Files.createTempDirectory("patch-summary-migration-retry")
        val recordPath = root.resolve("3867/summary.json")
        Files.createDirectories(recordPath.parent)
        val legacy = Json.encodeToString(PatchNoteRecord(article, summary, bodyImagesVersion = 2))
        Files.writeString(recordPath, legacy)
        var calls = 0
        var renders = 0
        val service = PatchNotesService(root, { listOf(article) }, { "正文" }, { _, _ -> if (++calls == 1) null else summary },
            render = { _, path -> if (++renders == 1) error("render failed") else png(path) })
        assertFailsWith<IllegalStateException> { service.scan() }
        assertEquals(legacy, Files.readString(recordPath))
        assertNull(service.latestImage())
        assertFailsWith<IllegalStateException> { service.scan() }
        assertEquals(PatchNotesService.SUMMARY_VERSION, Json.decodeFromString<PatchNoteRecord>(Files.readString(recordPath)).summaryVersion)
        assertNull(service.latestImage())
        assertEquals(1, service.scan())
        assertNotNull(service.latestImage())
        assertEquals(2, calls) // Successful revised summary survived the failed render.
        assertEquals(2, renders)
    }
}
