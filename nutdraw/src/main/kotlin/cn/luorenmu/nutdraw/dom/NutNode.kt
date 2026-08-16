package cn.luorenmu.nutdraw.dom

import cn.luorenmu.nutdraw.css.CssStyle

/**
 *
 * @author LoMu
 * Date 2026/8/16 15:30
 */
sealed class NutNode(open val style: CssStyle, open val id: String? = null) {
    fun findById(target: String): NutNode? {
        if (id == target) return this
        return (this as? NutElement)?.children?.firstNotNullOfOrNull { it.findById(target) }
    }

    /** Resolves nested id paths such as `body/rank/chart`. */
    operator fun get(path: String): NutNode? = path.split('/').filter(String::isNotBlank).fold(this as NutNode?) { node, part ->
        (node as? NutElement)?.children?.firstNotNullOfOrNull { it.findById(part) }
    }
}
