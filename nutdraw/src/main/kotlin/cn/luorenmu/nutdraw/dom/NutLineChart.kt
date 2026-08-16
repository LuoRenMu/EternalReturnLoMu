package cn.luorenmu.nutdraw.dom

import cn.luorenmu.nutdraw.css.CssStyle

/**
 *
 * @author LoMu
 * Date 2026/8/16 15:30
 */
data class NutLineChart(
    val labels: List<String>,
    val values: List<Int>,
    val lineColor: Int,
    override val style: CssStyle = CssStyle(),
    override val id: String? = null,
) : NutNode(style, id)
