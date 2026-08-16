package cn.luorenmu.nutdraw.layout

import cn.luorenmu.nutdraw.dom.NutNode
import org.jetbrains.skia.Rect

/**
 *
 * @author LoMu
 * Date 2026/8/16 15:30
 */
data class LayoutBox(
    val node: NutNode,
    val bounds: Rect,
    val children: List<LayoutBox> = emptyList(),
)
