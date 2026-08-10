package cn.luorenmu.nutdraw.layout

import cn.luorenmu.nutdraw.dom.NutNode
import org.jetbrains.skia.Rect

data class LayoutBox(
    val node: NutNode,
    val bounds: Rect,
    val children: List<LayoutBox> = emptyList(),
)
