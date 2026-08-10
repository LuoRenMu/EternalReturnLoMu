package cn.luorenmu.nutdraw.dom

import cn.luorenmu.nutdraw.css.CssStyle

data class NutImage(val source: String?, override val style: CssStyle = CssStyle(), override val id: String? = null) : NutNode(style, id)
