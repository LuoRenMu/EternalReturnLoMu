package cn.luorenmu.nutdraw.dom

import cn.luorenmu.nutdraw.css.CssStyle

fun document(style: CssStyle, id: String? = null, content: ElementBuilder.() -> Unit): NutElement =
    ElementBuilder(style, id).apply(content).build()
