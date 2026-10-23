package cn.luorenmu.nutdraw

import cn.luorenmu.nutdraw.render.CjkFontResolver
import org.jetbrains.skia.FontStyle
import cn.luorenmu.nutdraw.render.TypefaceResolver
import kotlin.test.*

class AsciiFontMetricsTest {
    @Test fun `numeric metrics use bundled Latin font independent of installed system fonts`() {
        val resolver = TypefaceResolver()
        for (style in listOf(FontStyle.NORMAL, FontStyle.BOLD)) {
            for (cp in 32..126) {
                val face = resolver.resolve(cp,style)
                assertEquals("Noto Sans",face.familyName)
                assertTrue(face.getUTF32Glyph(cp).toInt() != 0,"Missing ASCII glyph $cp")
            }
        }
    }

    @Test fun `six digit RP and signed change fit fixed hundred pixel column at unchanged font size`() {
        val resolver = CjkFontResolver()
        for (text in listOf("12345", "123456 (+123)", "123456 (-123)", "999999 (+999)")) {
            val width = resolver.runs(text,14f,400).sumOf { it.width.toDouble() }
            assertTrue(width <= 100f,"$text measured $width px at 14px")
        }
    }
}
