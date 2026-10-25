package cn.luorenmu.plugins.patchnotes

import cn.luorenmu.nutdraw.NutDraw
import cn.luorenmu.nutdraw.dom.NutImage
import cn.luorenmu.nutdraw.layout.FlexLayoutEngine
import cn.luorenmu.nutdraw.layout.LayoutBox
import kotlinx.coroutines.runBlocking
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.*

class PatchBodyImagesTest {
    private val articleUrl = "https://playeternalreturn.com/posts/news/3867"
    private fun png(width: Int, height: Int): ByteArray = ByteArrayOutputStream().use {
        ImageIO.write(BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", it)
        it.toByteArray()
    }

    @Test fun `extract only official body still images and preserve heading context`() {
        val html = """<img src="https://cdn.playeternalreturn.com/cover.png">
            <article><h3>新角色</h3><img src="//cdn.playeternalreturn.com/hero.png">
            <img src="https://evil.example/attack.png"><img src="http://cdn.playeternalreturn.com/http.png">
            <img src="https://cdn.playeternalreturn.com/icon.png" width="64">
            <img src="https://cdn.playeternalreturn.com/animation.gif">
            <h3>新皮肤</h3><img data-src="https://cdn.playeternalreturn.com/skin.png"></article>
            <footer><img src="https://cdn.playeternalreturn.com/footer.png"></footer>"""
        assertEquals(listOf("https://cdn.playeternalreturn.com/hero.png" to "新角色",
            "https://cdn.playeternalreturn.com/skin.png" to "新皮肤"), PatchBodyImages().candidates(html, articleUrl))
    }

    @Test fun `filter decoded skill icons save photos under article ID and reuse local files`() = runBlocking {
        val dir = Files.createTempDirectory("patch-body-images").resolve("3867")
        var requests = 0
        val collector = PatchBodyImages { url -> requests++; if (url.endsWith("icon.png")) png(64, 64) else png(640, 360) }
        val html = "<article><h3>正文</h3><img src='https://cdn.playeternalreturn.com/icon.png'><img src='https://cdn.playeternalreturn.com/hero.png'></article>"
        val photos = collector.collect(html, articleUrl, dir)
        assertEquals(1, photos.size)
        assertEquals(640, photos.single().width)
        assertTrue(Files.isRegularFile(dir.resolve(photos.single().fileName)))
        collector.collect(html, articleUrl, dir)
        assertEquals(3, requests, "Only uncached small icon is retried, body photo is local")
    }

    @Test fun `gallery retains whole pictures and stays inside document without URL text`() {
        val image = PatchBodyImage("https://cdn.playeternalreturn.com/hero.png", "新角色", "media/${"a".repeat(20)}.png", 1000, 560)
        val record = PatchNoteRecord(PatchArticle(3867, "测试", "2026-10-01", "2026-10-01", articleUrl),
            PatchSummary("摘要", listOf(PatchSection("重点", listOf("说明")))), List(3) { image }, 1)
        val doc = PatchNotesTemplate().build(record)
        val layout = FlexLayoutEngine().layout(doc.root, doc.width.toFloat(), doc.height.toFloat())
        val gallery = assertNotNull(layout.find("patch-body-images"))
        assertTrue(gallery.bounds.bottom < assertNotNull(layout.find("patch-section-0")).bounds.top)
        repeat(3) {
            val box = assertNotNull(layout.find("patch-body-image-$it"))
            assertTrue(box.bounds.bottom <= gallery.bounds.bottom)
            assertTrue(box.node is NutImage)
            assertEquals(cn.luorenmu.nutdraw.css.ObjectFit.CONTAIN, box.node.style.objectFit)
        }
    }

    @Test fun `AI can select later article images and only selected photos are acquired first`() = runBlocking {
        val html = "<article><h3>新角色</h3>" + (0..30).joinToString("") {
            "<p>角色图 $it</p><p><img src='https://cdn.playeternalreturn.com/$it.png'></p>"
        } + "</article>"
        val requests = mutableListOf<String>()
        val collector = PatchBodyImages(select = { _, pool ->
            assertEquals(31, pool.size)
            assertTrue(pool[30].context.contains("角色图 30"))
            listOf(30, 29, 28)
        }, download = { url -> requests += url; png(640, 360) })
        val photos = collector.collect(html, articleUrl, Files.createTempDirectory("patch-select"),
            PatchSummary("重点", listOf(PatchSection("角色", listOf("新增角色")))))
        assertEquals(listOf("30.png", "29.png", "28.png"), photos.map { it.sourceUrl.substringAfterLast('/') })
        assertEquals(3, requests.size)
    }

    @Test fun `AI failure falls back to diverse chapters rather than first three images`() = runBlocking {
        val html = "<article><h3>新角色</h3>" + (0..2).joinToString("") { "<img src='https://cdn.playeternalreturn.com/$it.png'>" } +
            "<h3>新皮肤</h3><img src='https://cdn.playeternalreturn.com/skin.png'><h3>系统</h3><img src='https://cdn.playeternalreturn.com/system.png'></article>"
        val photos = PatchBodyImages(select = { _, _ -> error("AI timeout") }, download = { png(640, 360) })
            .collect(html, articleUrl, Files.createTempDirectory("patch-fallback"),
                PatchSummary("重点", listOf(PatchSection("系统", listOf("功能调整")))))
        assertEquals(listOf("新角色", "新皮肤", "系统"), photos.map { it.caption })
    }

    @Test fun `invalid image bytes fail integrity check and remain retryable`() = runBlocking {
        val collector = PatchBodyImages { "invalid content".repeat(5).toByteArray() }
        assertFailsWith<IllegalStateException> {
            collector.collect("<article><img src='https://cdn.playeternalreturn.com/broken.png'></article>",
                articleUrl, Files.createTempDirectory("patch-invalid-photo"))
        }
        Unit
    }

    @Test fun `actual article 3867 body photos preview when live fixture is available`() = runBlocking {
        val root = Path.of(System.getProperty("patch.design.root"))
        val html = root.resolve("build/patch-fixtures/3867.html")
        if (!Files.exists(html) || System.getenv("PATCH_BODY_IMAGE_PREVIEW") != "1") return@runBlocking
        val dir = root.resolve("build/patch-image-selection-preview/3867")
        val photos = PatchBodyImages().collect(Files.readString(html), articleUrl, dir)
        assertEquals(3, photos.size)
        assertTrue(photos.none { it.sourceUrl.contains("upload/2026/09/30/7TwTg") }, "No news-list cover")
        val record = PatchNoteRecord(PatchArticle(3867, "2026.10.01 - 12.5 更新日志", "2026-10-01", "2026-10-01", articleUrl),
            PatchSummary("配图排版预览：下方均为文章 3867 的官方正文图片，不是列表封面。摘要尚未调用 AI，此图仅用于验证正文图片展示。",
                listOf(PatchSection("配图说明", listOf("自动提取正文候选并按章节分散选择，图片按原比例显示。", "生成摘要后由 AI 根据章节与周边文字选择相关配图，并按文章 ID 缓存。")))), photos, 2)
        val target = root.resolve("build/previews/patch-notes-3867-preview.png")
        NutDraw.render(PatchNotesTemplate(dir), record, target)
        assertTrue(Files.size(target) > 10_000)
        println("Article 3867 body photos rendered: count=${photos.size}; preview=$target")
    }

    private fun LayoutBox.find(id: String): LayoutBox? = takeIf { node.id == id } ?: children.firstNotNullOfOrNull { it.find(id) }
}
