package cn.luorenmu.nutdraw.render

import org.jetbrains.skia.Font

data class FontRun(val text: String, val font: Font) {
    val width: Float get() = font.measureTextWidth(text)
}
