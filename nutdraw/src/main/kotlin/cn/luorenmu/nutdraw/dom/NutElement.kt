package cn.luorenmu.nutdraw.dom

import cn.luorenmu.nutdraw.css.CssStyle

/**
 *
 * @author LoMu
 * Date 2026/8/16 15:30
 */
data class NutElement(
    override val style: CssStyle = CssStyle(),
    val children: List<NutNode> = emptyList(),
    override val id: String? = null,
) : NutNode(style, id)
