package cn.luorenmu.nutdraw.dom

import cn.luorenmu.nutdraw.css.CssStyle

/**
 *
 * @author LoMu
 * Date 2026/8/16 15:30
 */
data class NutText(val value: String, override val style: CssStyle = CssStyle(), override val id: String? = null) : NutNode(style, id)
