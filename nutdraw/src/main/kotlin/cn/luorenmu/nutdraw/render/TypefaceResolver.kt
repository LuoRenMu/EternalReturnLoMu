package cn.luorenmu.nutdraw.render

import org.jetbrains.skia.Data
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle
import org.jetbrains.skia.FontVariation
import org.jetbrains.skia.Typeface
import java.util.concurrent.ConcurrentHashMap

/**
 *
 * @author LoMu
 * Date 2026/8/16 15:30
 */
class TypefaceResolver(
    private val fontManager: FontMgr = FontMgr.default,
    private val config: FontFallbackConfig = FontFallbackConfig(),
) {
    private val cache = ConcurrentHashMap<Long, Typeface>()
    private val families = config.families.toTypedArray()
    private val languages = config.languages.toTypedArray()
    private val koreanTypefaces: Pair<Typeface, Typeface> by lazy {
        val bytes = checkNotNull(javaClass.getResourceAsStream("/fonts/NotoSansKR-wght.ttf")) {
            "Bundled Noto Sans KR font is missing"
        }.use { it.readBytes() }
        val regular = checkNotNull(fontManager.makeFromData(Data.makeFromBytes(bytes))) {
            "Bundled Noto Sans KR font cannot be loaded"
        }
        regular to regular.makeClone(FontVariation("wght", 700f))
    }

    // System fallback glyph advances differ across hosts (e.g. DejaVu Sans makes a 14px RP value 107px).
    // Bundle Latin glyphs so fixed-width numeric columns measure and render identically everywhere.
    private val asciiTypefaces: Pair<Typeface, Typeface> by lazy {
        val bytes = checkNotNull(javaClass.getResourceAsStream("/fonts/latin/NotoSans-wdth-wght.ttf")) {
            "Bundled Noto Sans Latin font is missing"
        }.use { it.readBytes() }
        val regular = Data.makeFromBytes(bytes).use { data ->
            checkNotNull(fontManager.makeFromData(data)) { "Bundled Noto Sans Latin font cannot be loaded" }
        }
        regular to regular.makeClone(FontVariation("wght", 700f))
    }

    fun resolve(codePoint: Int, style: FontStyle): Typeface {
        val key = (style.weight.toLong() shl 32) or codePoint.toLong()
        return cache.computeIfAbsent(key) {
            if (codePoint in 32..126) {
                return@computeIfAbsent if (style.weight >= FontStyle.BOLD.weight) asciiTypefaces.second else asciiTypefaces.first
            }
            if (Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HANGUL) {
                val bundled = if (style.weight >= FontStyle.BOLD.weight) koreanTypefaces.second else koreanTypefaces.first
                if (bundled.getUTF32Glyph(codePoint).toInt() != 0) return@computeIfAbsent bundled
            }
            fontManager.matchFamiliesStyleCharacter(families, style, languages, codePoint)
                ?: fontManager.matchFamiliesStyle(families, style)
                ?: checkNotNull(fontManager.legacyMakeTypeface(config.finalFallback, style))
        }
    }
}
