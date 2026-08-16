package cn.luorenmu.nutdraw.render

import cn.luorenmu.nutdraw.dom.NutLineChart
import org.jetbrains.skia.*
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/**
 * Native Skia equivalent of the small Chart.js MMR history chart.
 *
 * @author LoMu
 * Date 2026/8/16 15:30
 */
class LineChartRenderer(private val fonts: CjkFontResolver) {
    fun draw(canvas: Canvas, chart: NutLineChart, bounds: Rect) {
        val count = minOf(chart.labels.size, chart.values.size)
        if (count == 0) return
        val values = chart.values.take(count)
        val minValue = floor(values.min() / 100.0).toInt() * 100
        val maxValue = max(minValue + 100, ceil(values.max() / 100.0).toInt() * 100)
        val plot = Rect.makeLTRB(bounds.left + 38f, bounds.top + 8f, bounds.right - 8f, bounds.bottom - 24f)
        val axis = Paint().apply { color = Color.makeRGB(205, 205, 205); strokeWidth = 1f; mode = PaintMode.STROKE }
        canvas.drawLine(plot.left, plot.top, plot.left, plot.bottom, axis)
        canvas.drawLine(plot.left, plot.bottom, plot.right, plot.bottom, axis)
        val labelPaint = Paint().apply { color = Color.makeRGB(128, 128, 128); isAntiAlias = true }
        val labelFont = fonts.runs("0", 9f, 400).first().font
        for (step in 0..2) {
            val value = minValue + (maxValue - minValue) * step / 2
            val y = plot.bottom - plot.height * step / 2f
            canvas.drawString(value.toString(), bounds.left + 2f, y + 3f, labelFont, labelPaint)
        }
        val points = values.mapIndexed { index, value ->
            val x = if (count == 1) plot.left + plot.width / 2 else plot.left + plot.width * index / (count - 1)
            val y = plot.bottom - plot.height * (value - minValue) / (maxValue - minValue).toFloat()
            x to y
        }
        val line = Paint().apply { color = chart.lineColor; strokeWidth = 2f; mode = PaintMode.STROKE; isAntiAlias = true }
        points.zipWithNext().forEach { (a, b) -> canvas.drawLine(a.first, a.second, b.first, b.second, line) }
        val dot = Paint().apply { color = chart.lineColor; isAntiAlias = true }
        points.forEachIndexed { index, point ->
            canvas.drawCircle(point.first, point.second, 4f, dot)
            val runs = fonts.runs(chart.labels[index], 8f, 400)
            val width = runs.sumOf { it.width.toDouble() }.toFloat()
            var x = (point.first - width / 2).coerceIn(bounds.left, bounds.right - width)
            runs.forEach { run -> canvas.drawString(run.text, x, bounds.bottom - 6f, run.font, labelPaint); x += run.width }
        }
    }
}
