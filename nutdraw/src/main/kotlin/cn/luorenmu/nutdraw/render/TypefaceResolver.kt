package cn.luorenmu.nutdraw.render

import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle
import org.jetbrains.skia.Typeface
import java.util.concurrent.ConcurrentHashMap

class TypefaceResolver(
    private val fontManager: FontMgr = FontMgr.default,
    private val config: FontFallbackConfig = FontFallbackConfig(),
) {
    private val cache = ConcurrentHashMap<Long, Typeface>()

    fun resolve(codePoint: Int, style: FontStyle): Typeface {
        val key = (style.weight.toLong() shl 32) or codePoint.toLong()
        return cache.computeIfAbsent(key) {
            fontManager.matchFamiliesStyleCharacter(config.families, style, config.languages, codePoint)
                ?: fontManager.matchFamiliesStyle(config.families, style)
                ?: checkNotNull(fontManager.legacyMakeTypeface(config.finalFallback, style))
        }
    }
}
