package cn.luorenmu.nutdraw.dom

import cn.luorenmu.nutdraw.css.CssStyle

data class NutElement(
    override val style: CssStyle = CssStyle(),
    val children: List<NutNode> = emptyList(),
    override val id: String? = null,
) : NutNode(style, id)
