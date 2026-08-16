package cn.luorenmu.nutdraw

import cn.luorenmu.nutdraw.css.CssStyle
import cn.luorenmu.nutdraw.css.px
import cn.luorenmu.nutdraw.dom.document
import cn.luorenmu.nutdraw.render.SkiaDocumentRenderer
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.Color
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 *
 * @author LoMu
 * Date 2026/8/16 15:30
 */
class LineChartRenderingTest {
    @Test
    fun `renders chart js equivalent mmr line chart`() = runBlocking {
        val output = Path.of(System.getProperty("user.dir"), "build", "previews", "mmr-chart-skia.png")
        val root = document(CssStyle(width = px(370), height = px(150), background = Color.WHITE)) {
            lineChart(listOf("8/04", "8/05", "8/06", "8/07", "8/08", "8/09", "8/10"), listOf(6400, 6510, 6460, 6640, 6720, 6690, 6842), Color.makeRGB(202, 164, 40), CssStyle(width = px(370), height = px(150)))
        }
        SkiaDocumentRenderer().render(root, output, 370, 150)
        assertTrue(Files.size(output) > 1_000)
    }
}
