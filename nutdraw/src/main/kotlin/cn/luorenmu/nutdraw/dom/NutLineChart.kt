package cn.luorenmu.nutdraw.dom

import cn.luorenmu.nutdraw.css.CssStyle

data class NutLineChart(
    val labels: List<String>,
    val values: List<Int>,
    val lineColor: Int,
    override val style: CssStyle = CssStyle(),
    override val id: String? = null,
) : NutNode(style, id)
