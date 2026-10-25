package cn.luorenmu.nutdraw

import cn.luorenmu.nutdraw.render.TypefaceResolver
import org.jetbrains.skia.FontStyle
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class KoreanTypefaceTest {
    @Test
    fun `Hangul uses the bundled Korean font independent of system fonts`() {
        val resolver = TypefaceResolver()
        for (style in listOf(FontStyle.NORMAL, FontStyle.BOLD)) {
            val typeface = resolver.resolve('한'.code, style)
            assertTrue(typeface.familyName.contains("Noto Sans KR"), typeface.familyName)
            assertNotEquals(0, typeface.getUTF32Glyph('한'.code).toInt())
            assertNotEquals(0, typeface.getUTF32Glyph('글'.code).toInt())
            for (character in "백수삼다수는맛있어한글한국어해피라온")
                assertNotEquals(0, resolver.resolve(character.code, style).getUTF32Glyph(character.code).toInt(), character.toString())
        }
    }

    @Test fun `mixed Chinese Korean and Latin text keeps Korean glyphs and fixed font size`() {
        val runs = cn.luorenmu.nutdraw.render.CjkFontResolver().runs("玩家 백수 Player", 24f, 700)
        assertTrue(runs.joinToString("") { it.text } == "玩家 백수 Player")
        for (run in runs) {
            assertTrue(run.font.size == 24f)
            for (character in run.text.filter { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HANGUL })
                assertNotEquals(0, run.font.typeface!!.getUTF32Glyph(character.code).toInt())
        }
    }
}
