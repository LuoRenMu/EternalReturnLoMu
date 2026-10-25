package cn.luorenmu.plugins.patchnotes

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.awt.image.BufferedImage
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.*

class LatestPatchNotesTest {
    private val older = PatchArticle(3867, "old", "2026-10-01T00:00:00Z", "2026-10-01T00:00:00Z", "https://playeternalreturn.com/posts/news/3867")
    private val newer = older.copy(id = 4000, title = "new", createdAt = "2026-10-02T00:00:00Z")
    private val summary = PatchSummary("摘要", listOf(PatchSection("系统", listOf("重点"))))

    @Test fun `only newest is processed and cached newest never triggers historical backfill`() = runBlocking {
        val root = Files.createTempDirectory("latest-only")
        val fetched = mutableListOf<Int>()
        val service = PatchNotesService(root, { listOf(older, newer) }, { fetched += it.id; "正文" },
            { _, _ -> summary }, render = { _, path -> ImageIO.write(BufferedImage(4, 4, 1), "png", path.toFile()); Unit })
        assertEquals(1, service.scan())
        assertEquals(0, service.scan())
        assertEquals(listOf(4000), fetched)
        assertFalse(Files.exists(root.resolve("3867")))
        assertEquals(root.resolve("4000/summary.png"), service.latestImage())
    }

    @Test fun `publication date wins over pin order and equal dates use article ID`() = runBlocking {
        val root = Files.createTempDirectory("latest-date")
        val fetched = mutableListOf<Int>()
        val winner = newer.copy(id = 4100)
        val service = PatchNotesService(root, { listOf(older.copy(id = 9999), newer, winner) },
            { fetched += it.id; "正文" }, { _, _ -> summary },
            render = { _, path -> ImageIO.write(BufferedImage(4, 4, 1), "png", path.toFile()); Unit })
        service.scan()
        assertEquals(listOf(4100), fetched)
    }

    @Test fun `newest pending does not block query or return old cached update`() = runBlocking {
        val root = Files.createTempDirectory("latest-pending")
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var listing = listOf(older)
        val service = PatchNotesService(root, { listing }, { "正文" }, { title, _ ->
            if (title == "new") { started.complete(Unit); release.await() }
            summary
        }, render = { _, path -> ImageIO.write(BufferedImage(4, 4, 1), "png", path.toFile()); Unit })
        service.scan()
        assertNotNull(service.latestImage())
        listing = listOf(older, newer)
        val scan = async { service.scan() }
        try {
            started.await()
            assertNull(withTimeout(1_000) { service.latestImage() })
        } finally { release.complete(Unit) }
        scan.await()
        assertEquals(root.resolve("4000/summary.png"), service.latestImage())
    }

    @Test fun `newest failure never falls back to processing an older article`() = runBlocking {
        val root = Files.createTempDirectory("latest-failure")
        val fetched = mutableListOf<Int>()
        val service = PatchNotesService(root, { listOf(older, newer) }, { fetched += it.id; "正文" }, { _, _ -> null })
        assertFailsWith<IllegalStateException> { service.scan() }
        assertEquals(listOf(4000), fetched)
        assertNull(service.latestImage())
        assertFalse(Files.exists(root.resolve("3867")))
    }

    @Test fun `summary format migration touches only newest article and preserves historical cache`() = runBlocking {
        val root = Files.createTempDirectory("latest-summary-migration")
        val legacy = PatchNoteRecord(older, summary, bodyImagesVersion = 2)
        for (article in listOf(older, newer)) {
            val recordPath = root.resolve("${article.id}/summary.json")
            Files.createDirectories(recordPath.parent)
            Files.writeString(recordPath, Json.encodeToString(legacy.copy(article = article)))
        }
        val oldPath = root.resolve("3867/summary.json")
        val oldContent = Files.readString(oldPath)
        val fetched = mutableListOf<Int>()
        val service = PatchNotesService(root, { listOf(older, newer) }, { fetched += it.id; "正文" }, { _, _ -> summary },
            render = { _, path -> ImageIO.write(BufferedImage(4, 4, 1), "png", path.toFile()); Unit })
        assertNull(service.latestImage())
        assertEquals(1, service.scan())
        assertEquals(0, service.scan())
        assertEquals(listOf(4000), fetched)
        assertEquals(oldContent, Files.readString(oldPath))
        assertEquals(root.resolve("4000/summary.png"), service.latestImage())
    }
}
