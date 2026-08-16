package cn.luorenmu.nutdraw.render

import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle
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

    fun resolve(codePoint: Int, style: FontStyle): Typeface {
        val key = (style.weight.toLong() shl 32) or codePoint.toLong()
        return cache.computeIfAbsent(key) {
            fontManager.matchFamiliesStyleCharacter(families, style, languages, codePoint)
                ?: fontManager.matchFamiliesStyle(families, style)
                ?: checkNotNull(fontManager.legacyMakeTypeface(config.finalFallback, style))
        }
    }
}
