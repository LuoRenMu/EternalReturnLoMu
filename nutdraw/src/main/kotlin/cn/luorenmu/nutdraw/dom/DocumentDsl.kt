@file:Suppress("FunctionName")

package cn.luorenmu.nutdraw.dom

import cn.luorenmu.nutdraw.css.CssStyle

@DslMarker
annotation class NutDrawDsl

fun document(style: CssStyle, id: String? = null, content: ElementBuilder.() -> Unit): NutElement =
    ElementBuilder(style, id).apply(content).build()

fun Document(style: CssStyle, id: String? = null, content: ElementBuilder.() -> Unit): NutElement =
    document(style, id, content)
