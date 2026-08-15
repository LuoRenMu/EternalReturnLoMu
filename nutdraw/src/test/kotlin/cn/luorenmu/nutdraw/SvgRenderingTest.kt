package cn.luorenmu.nutdraw

import cn.luorenmu.nutdraw.css.CssStyle
import cn.luorenmu.nutdraw.css.ObjectFit
import cn.luorenmu.nutdraw.css.px
import cn.luorenmu.nutdraw.dom.document
import cn.luorenmu.nutdraw.render.SkiaDocumentRenderer
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.Color
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SvgRenderingTest {
    @Test
    fun `renders svg resources instead of silently dropping them`() {
        runBlocking {
            val svg = Files.createTempFile("nutdraw-svg-", ".svg")
            Files.writeString(svg, """<svg xmlns="http://www.w3.org/2000/svg" width="80" height="80"><rect width="80" height="80" fill="#ff4655"/><circle cx="40" cy="40" r="20" fill="white"/></svg>""")
            val output = Path("build/previews/svg-rendering-skia.png")
            val root = document(CssStyle(width = px(100), height = px(100), background = Color.BLACK)) {
                image(svg.toUri().toString(), CssStyle(width = px(80), height = px(80), objectFit = ObjectFit.CONTAIN))
            }
            SkiaDocumentRenderer().render(root, output, 100, 100)
            assertTrue(Files.size(output) > 500)
            Files.deleteIfExists(svg)
        }
    }

    @Test
    fun `scales fixed size svg to a smaller element and keeps its centre visible`() {
        runBlocking {
            val svg = Files.createTempFile("nutdraw-fixed-svg-", ".svg")
            Files.writeString(svg, """<svg xmlns="http://www.w3.org/2000/svg" width="60" height="60" viewBox="0 0 60 60"><rect width="60" height="60" fill="#ff0000"/><circle cx="30" cy="30" r="12" fill="#ffffff"/></svg>""")
            val output = Path("build/previews/svg-fixed-size-cover-skia.png")
            val root = document(CssStyle(width = px(20), height = px(20), background = Color.BLACK)) {
                element(CssStyle(width = px(20), height = px(20), backgroundImage = svg.toUri().toString()))
            }

            SkiaDocumentRenderer().render(root, output, 20, 20)

            val preview = ImageIO.read(output.toFile())
            assertEquals(0xFFFFFF, preview.getRGB(10, 10) and 0xFFFFFF)
            assertEquals(0xFF0000, preview.getRGB(1, 1) and 0xFFFFFF)
            Files.deleteIfExists(svg)
        }
    }
}
