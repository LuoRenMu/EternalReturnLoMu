package cn.luorenmu.nutdraw.dom

import cn.luorenmu.nutdraw.css.CssStyle

data class NutText(val value: String, override val style: CssStyle = CssStyle(), override val id: String? = null) : NutNode(style, id)
