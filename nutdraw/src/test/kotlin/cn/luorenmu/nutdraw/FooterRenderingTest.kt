package cn.luorenmu.nutdraw

import cn.luorenmu.nutdraw.css.CssStyle
import cn.luorenmu.nutdraw.css.px
import cn.luorenmu.nutdraw.dom.document
import cn.luorenmu.nutdraw.render.NUTDRAW_FOOTER_HEIGHT
import cn.luorenmu.nutdraw.render.NUTDRAW_FOOTER_TEXT
import cn.luorenmu.nutdraw.render.SkiaDocumentRenderer
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Color
import org.jetbrains.skia.Image
import java.nio.file.Files
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 *
 * @author LoMu
 * Date 2026/8/16 15:30
 */
class FooterRenderingTest {
    @Test
    fun `appends the fixed NutDraw credit below document content`() = runBlocking {
        val width = 360
        val contentHeight = 80
        val output = Path("build/previews/footer-rendering-skia.png")
        val root = document(CssStyle(width = px(width), height = px(contentHeight), background = Color.WHITE)) {}

        SkiaDocumentRenderer().render(root, output, width, contentHeight)

        Image.makeFromEncoded(Files.readAllBytes(output)).use { image ->
            assertEquals(width, image.width)
            assertEquals(contentHeight + NUTDRAW_FOOTER_HEIGHT, image.height)
            Bitmap().use { bitmap ->
                bitmap.allocN32Pixels(image.width, image.height)
                image.readPixels(bitmap)
                assertNotEquals(Color.WHITE, bitmap.getColor(0, contentHeight + 1))
            }
        }
        assertEquals("Power By EternalReturnLoMu & LuoRenMu", NUTDRAW_FOOTER_TEXT)
    }
}
