package cn.luorenmu.nutdraw.dom

import cn.luorenmu.nutdraw.css.CssStyle

class ElementBuilder(private val style: CssStyle, private val id: String? = null) {
    private val children = mutableListOf<NutNode>()

    fun element(style: CssStyle = CssStyle(), id: String? = null, content: ElementBuilder.() -> Unit = {}) {
        children += ElementBuilder(style, id).apply(content).build()
    }

    fun text(value: Any?, style: CssStyle = CssStyle(), id: String? = null) {
        children += NutText(value?.toString().orEmpty(), style, id)
    }

    fun image(source: String?, style: CssStyle = CssStyle(), id: String? = null) {
        children += NutImage(source, style, id)
    }

    fun lineChart(labels: List<String>, values: List<Int>, lineColor: Int, style: CssStyle = CssStyle(), id: String? = null) {
        children += NutLineChart(labels, values, lineColor, style, id)
    }

    fun node(node: NutNode) { children += node }
    fun build() = NutElement(style, children.toList(), id)
}
