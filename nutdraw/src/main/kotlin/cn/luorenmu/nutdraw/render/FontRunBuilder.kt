package cn.luorenmu.nutdraw.render

import org.jetbrains.skia.Font
import org.jetbrains.skia.FontStyle
import org.jetbrains.skia.Typeface

class FontRunBuilder(private val typefaces: TypefaceResolver = TypefaceResolver()) {
    fun build(text: String, size: Float, weight: Int): List<FontRun> {
        if (text.isEmpty()) return emptyList()
        val result = mutableListOf<FontRun>()
        val style = if (weight >= 600) FontStyle.BOLD else FontStyle.NORMAL
        var currentTypeface: Typeface? = null
        val current = StringBuilder()
        fun flush() {
            if (current.isNotEmpty()) {
                result += FontRun(current.toString(), Font(checkNotNull(currentTypeface), size))
                current.clear()
            }
        }
        text.codePoints().forEach { codePoint ->
            val typeface = typefaces.resolve(codePoint, style)
            if (currentTypeface?.uniqueId != typeface.uniqueId) {
                flush()
                currentTypeface = typeface
            }
            current.append(String(Character.toChars(codePoint)))
        }
        flush()
        return result
    }
}
