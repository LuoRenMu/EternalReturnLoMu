package cn.luorenmu.nutdraw.template

import cn.luorenmu.nutdraw.css.*
import org.jetbrains.skia.Color

/**
 *
 * @author LoMu
 * Date 2026/8/16 15:30
 */
object TemplateStyles {
    val background = Color.makeRGB(15, 17, 23)
    val card = Color.makeRGB(26, 29, 39)
    val border = Color.makeRGB(42, 45, 58)
    val text = Color.makeRGB(225, 228, 237)
    val muted = Color.makeRGB(139, 143, 163)
    val accent = Color.makeRGB(108, 140, 255)
    val green = Color.makeRGB(158, 206, 106)
    val gold = Color.makeRGB(245, 197, 66)

    fun page(width: Int, height: Int) = CssStyle(width = px(width), height = px(height), padding = Edges(28f), gap = 16f, background = background, color = text)
    fun row(gap: Float = 12f, height: CssSize = CssSize.Auto) = CssStyle(direction = FlexDirection.ROW, alignItems = AlignItems.CENTER, gap = gap, height = height)
    fun card(height: Float, width: CssSize = percent(100)) = CssStyle(width = width, height = px(height), padding = Edges(16f), gap = 12f, background = card, border = Border(1f, border), borderRadius = 10f)
    fun title(size: Float = 32f) = CssStyle(height = px(size * 1.4f), fontSize = size, fontWeight = 700, color = text)
    fun muted(size: Float = 15f) = CssStyle(height = px(size * 1.4f), fontSize = size, color = muted)
    fun value(size: Float = 18f, color: Int = text) = CssStyle(height = px(size * 1.4f), fontSize = size, fontWeight = 600, color = color)
    fun image(width: Float, height: Float) = CssStyle(width = px(width), height = px(height), borderRadius = 8f, background = border)
}
