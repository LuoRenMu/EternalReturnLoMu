package cn.luorenmu.nutdraw

import cn.luorenmu.nutdraw.css.CssStyle
import cn.luorenmu.nutdraw.css.ObjectFit
import cn.luorenmu.nutdraw.css.px
import cn.luorenmu.nutdraw.dom.document
import cn.luorenmu.nutdraw.render.SkiaDocumentRenderer
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.Color
import java.nio.file.Files
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class SvgRenderingTest {
    @Test
    fun `renders svg resources instead of silently dropping them`() = runBlocking {
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
