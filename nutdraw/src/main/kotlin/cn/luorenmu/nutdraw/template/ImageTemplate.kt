package cn.luorenmu.nutdraw.template

import cn.luorenmu.nutdraw.dom.NutNode

/**
 *
 * @author LoMu
 * Date 2026/8/16 15:30
 */
data class TemplateDocument(val width: Int, val height: Int, val root: NutNode)
fun interface ImageTemplate<T> { fun build(data: T): TemplateDocument }
