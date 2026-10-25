package cn.luorenmu.nutdraw

import cn.luorenmu.nutdraw.css.CssStyle
import cn.luorenmu.nutdraw.css.px
import cn.luorenmu.nutdraw.dom.document
import cn.luorenmu.nutdraw.render.ImageByteLoader
import cn.luorenmu.nutdraw.render.ImageLoader
import cn.luorenmu.nutdraw.render.ImageSource
import cn.luorenmu.nutdraw.render.SkiaDocumentRenderer
import cn.luorenmu.nutdraw.resource.ResourceManager
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

class ImagePrefetchTest {
    @Test
    fun `render overlaps unique remote resources and reuses their bytes`() = runBlocking {
        val active = AtomicInteger()
        val peak = AtomicInteger()
        val calls = AtomicInteger()
        val svg = "<svg xmlns='http://www.w3.org/2000/svg' width='10' height='10'><rect width='10' height='10' fill='red'/></svg>".toByteArray()
        val loader = ImageLoader(bytes = ImageByteLoader(listOf(ImageSource {
            calls.incrementAndGet()
            val concurrent = active.incrementAndGet()
            peak.updateAndGet { old -> maxOf(old, concurrent) }
            try { Thread.sleep(100); svg } finally { active.decrementAndGet() }
        })))
        val renderer = SkiaDocumentRenderer(resources = ResourceManager(images = loader))
        val directory = Files.createTempDirectory("image-prefetch-test")
        try {
            val empty = document(CssStyle(width = px(200), height = px(200))) {}
            renderer.render(empty, directory.resolve("warmup.png"), 200, 200)
            val root = document(CssStyle(width = px(200), height = px(200),
                backgroundImage = "https://fixture.invalid/0.svg")) {
                repeat(8) { image("https://fixture.invalid/$it.svg", CssStyle(width = px(10), height = px(10))) }
            }
            val elapsed = measureTimeMillis { renderer.render(root, directory.resolve("cold.png"), 200, 200) }
            println("[PERF] cold-render requests=${calls.get()} peak=${peak.get()} elapsed=${elapsed}ms")
            assertEquals(8, calls.get(), "Duplicate background/image source must load once")
            assertTrue(peak.get() > 1, "Remote byte loads should overlap")
            assertTrue(peak.get() <= 6, "Remote byte loads must remain bounded")
            renderer.render(root, directory.resolve("warm.png"), 200, 200)
            assertEquals(8, calls.get(), "Warm renders must not reload cached resources")
            assertContentEquals(Files.readAllBytes(directory.resolve("cold.png")),
                Files.readAllBytes(directory.resolve("warm.png")), "Prefetch must not alter rendered pixels")
        } finally { directory.toFile().deleteRecursively() }
    }

    @Test
    fun `failed prefetch is not retried during drawing but recovers on next render`() = runBlocking {
        val calls = AtomicInteger()
        val svg = "<svg xmlns='http://www.w3.org/2000/svg' width='10' height='10'><rect width='10' height='10' fill='red'/></svg>".toByteArray()
        val loader = ImageLoader(bytes = ImageByteLoader(listOf(ImageSource {
            if (calls.incrementAndGet() == 1) throw java.io.IOException("fixture failure")
            svg
        })))
        val renderer = SkiaDocumentRenderer(resources = ResourceManager(images = loader))
        val root = document(CssStyle(width = px(40), height = px(40))) {
            image("https://fixture.invalid/retry.svg", CssStyle(width = px(10), height = px(10)))
        }
        val directory = Files.createTempDirectory("image-prefetch-retry")
        try {
            renderer.render(root, directory.resolve("failed.png"), 40, 40)
            assertEquals(1, calls.get())
            renderer.render(root, directory.resolve("recovered.png"), 40, 40)
            assertEquals(2, calls.get())
            assertTrue(!Files.readAllBytes(directory.resolve("failed.png")).contentEquals(
                Files.readAllBytes(directory.resolve("recovered.png"))))
        } finally { directory.toFile().deleteRecursively() }
    }
}
