package cn.luorenmu.nutdraw.render

import org.jetbrains.skia.Font

/**
 *
 * @author LoMu
 * Date 2026/8/16 15:30
 */
data class FontRun(val text: String, val font: Font) {
    val width: Float get() = font.measureTextWidth(text)
}
