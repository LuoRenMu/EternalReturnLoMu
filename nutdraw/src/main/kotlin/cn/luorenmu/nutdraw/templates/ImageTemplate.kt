package cn.luorenmu.nutdraw.templates

import cn.luorenmu.nutdraw.dom.NutNode

data class TemplateDocument(val width: Int, val height: Int, val root: NutNode)
fun interface ImageTemplate<T> { fun build(data: T): TemplateDocument }
