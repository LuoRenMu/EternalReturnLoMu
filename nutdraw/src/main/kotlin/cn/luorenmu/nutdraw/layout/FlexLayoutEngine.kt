package cn.luorenmu.nutdraw.layout

import cn.luorenmu.nutdraw.css.*
import cn.luorenmu.nutdraw.dom.*
import org.jetbrains.skia.Rect
import kotlin.math.max

/**
 * Small deterministic CSS box/Flex layout engine for image documents.
 *
 * @author LoMu
 * Date 2026/8/16 15:30
 */
class FlexLayoutEngine {
    fun layout(root: NutNode, width: Float, height: Float): LayoutBox = layoutNode(root, 0f, 0f, width, height)

    private fun layoutNode(node: NutNode, x: Float, y: Float, availableWidth: Float, availableHeight: Float): LayoutBox {
        val s = node.style
        // Parent Flex layout has already resolved px/percent/flex sizes before recursion.
        // Treat the allocated box as authoritative to avoid resolving percentages twice.
        val width = availableWidth.coerceIn(s.minWidth, s.maxWidth)
        val height = availableHeight.coerceIn(s.minHeight, s.maxHeight)
        val bounds = Rect.makeXYWH(x + s.margin.left, y + s.margin.top, max(0f, width - s.margin.horizontal), max(0f, height - s.margin.vertical))
        if (node !is NutElement || node.children.isEmpty()) return LayoutBox(node, bounds)

        val innerX = bounds.left + s.padding.left
        val innerY = bounds.top + s.padding.top
        val innerW = max(0f, bounds.width - s.padding.horizontal)
        val innerH = max(0f, bounds.height - s.padding.vertical)
        val visible = node.children.filter { it.style.display != Display.NONE && it.style.position == Position.STATIC }
        val absolute = node.children.filter { it.style.display != Display.NONE && it.style.position == Position.ABSOLUTE }
        val rows = if (s.wrap == FlexWrap.WRAP && s.direction == FlexDirection.ROW) wrapRows(visible, innerW, s.gap) else listOf(visible)
        val boxes = mutableListOf<LayoutBox>()
        var crossCursor = 0f
        rows.forEach { row ->
            val horizontal = s.direction == FlexDirection.ROW
            val mainAvailable = if (horizontal) innerW else innerH
            val fixed = row.sumOf { child -> mainSize(child, mainAvailable, horizontal).toDouble() }.toFloat() + max(0, row.size - 1) * s.gap
            val growTotal = row.sumOf { it.style.flexGrow.toDouble() }.toFloat()
            val remaining = max(0f, mainAvailable - fixed)
            val sizes = row.map { mainSize(it, mainAvailable, horizontal) + if (growTotal > 0f) remaining * it.style.flexGrow / growTotal else 0f }
            val used = sizes.sum() + max(0, row.size - 1) * s.gap
            val (start, extraGap) = spacing(s.justifyContent, mainAvailable, used, row.size)
            var mainCursor = start
            var lineCross = 0f
            row.forEachIndexed { index, child ->
                val childMain = sizes[index]
                val childCross = crossSize(child, if (horizontal) innerH else innerW, horizontal)
                lineCross = max(lineCross, childCross)
                val childX = if (horizontal) innerX + mainCursor else innerX + alignOffset(s.alignItems, innerW, childCross)
                val childY = if (horizontal) innerY + crossCursor + alignOffset(s.alignItems, innerH, childCross) else innerY + mainCursor
                boxes += layoutNode(child, childX, childY, if (horizontal) childMain else childCross, if (horizontal) childCross else childMain)
                mainCursor += childMain + s.gap + extraGap
            }
            crossCursor += if (s.wrap == FlexWrap.WRAP) lineCross + s.gap else 0f
        }
        absolute.forEach { child ->
            val childWidth = resolve(child.style.width, innerW, innerW)
            val childHeight = resolve(child.style.height, innerH, intrinsicHeight(child, childWidth))
            val childX = innerX + (child.style.left ?: (innerW - (child.style.right ?: 0f) - childWidth))
            val childY = innerY + (child.style.top ?: (innerH - (child.style.bottom ?: 0f) - childHeight))
            boxes += layoutNode(child, childX, childY, childWidth, childHeight)
        }
        return LayoutBox(node, bounds, boxes)
    }

    private fun intrinsicHeight(node: NutNode, width: Float): Float = when (node) {
        is NutText -> node.style.fontSize * node.style.lineHeight + node.style.padding.vertical + node.style.margin.vertical
        is NutImage -> 120f
        is NutLineChart -> 160f
        is NutElement -> {
            val childHeights = node.children.map { resolve(it.style.height, Float.POSITIVE_INFINITY, intrinsicHeight(it, width)) }
            val content = if (node.style.direction == FlexDirection.ROW) childHeights.maxOrNull() ?: 0f else childHeights.sum() + max(0, childHeights.size - 1) * node.style.gap
            content + node.style.padding.vertical + node.style.margin.vertical
        }
    }

    private fun mainSize(node: NutNode, available: Float, horizontal: Boolean): Float = if (horizontal) resolve(node.style.width, available, 0f) else resolve(node.style.height, available, intrinsicHeight(node, available))
    private fun crossSize(node: NutNode, available: Float, horizontal: Boolean): Float = if (horizontal) resolve(node.style.height, available, intrinsicHeight(node, available)) else resolve(node.style.width, available, available)
    private fun resolve(size: CssSize, available: Float, auto: Float) = when (size) { CssSize.Auto -> auto; is CssSize.Px -> size.value; is CssSize.Percent -> available * size.value / 100f }
    private fun alignOffset(align: AlignItems, available: Float, size: Float) = when (align) { AlignItems.CENTER -> (available - size) / 2; AlignItems.END -> available - size; else -> 0f }.coerceAtLeast(0f)
    private fun spacing(justify: JustifyContent, available: Float, used: Float, count: Int): Pair<Float, Float> {
        val free = max(0f, available - used)
        return when (justify) {
            JustifyContent.CENTER -> free / 2 to 0f
            JustifyContent.END -> free to 0f
            JustifyContent.SPACE_BETWEEN -> 0f to if (count > 1) free / (count - 1) else 0f
            JustifyContent.SPACE_AROUND -> (if (count > 0) free / count / 2 else 0f) to (if (count > 0) free / count else 0f)
            else -> 0f to 0f
        }
    }
    private fun wrapRows(nodes: List<NutNode>, width: Float, gap: Float): List<List<NutNode>> {
        val result = mutableListOf<MutableList<NutNode>>()
        var used = 0f
        nodes.forEach {
            val size = resolve(it.style.width, width, width)
            if (result.isEmpty() || (result.last().isNotEmpty() && used + gap + size > width)) { result.add(mutableListOf()); used = 0f }
            if (result.last().isNotEmpty()) used += gap
            result.last().add(it)
            used += size
        }
        return result
    }
}
