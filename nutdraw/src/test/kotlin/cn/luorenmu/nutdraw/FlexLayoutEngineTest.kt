package cn.luorenmu.nutdraw

import cn.luorenmu.nutdraw.css.*
import cn.luorenmu.nutdraw.dom.document
import cn.luorenmu.nutdraw.layout.FlexLayoutEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 *
 * @author LoMu
 * Date 2026/8/16 15:30
 */
class FlexLayoutEngineTest {
    @Test
    fun distributesRemainingRowSpaceToFlexGrowChildren() {
        val root = document(CssStyle(direction = FlexDirection.ROW, width = px(300), height = px(100), gap = 10f)) {
            element(CssStyle(width = px(50), height = px(100)))
            element(CssStyle(flexGrow = 1f, height = px(100)))
        }
        val children = FlexLayoutEngine().layout(root, 300f, 100f).children
        assertEquals(2, children.size)
        assertEquals(50f, children[0].bounds.width)
        assertEquals(240f, children[1].bounds.width)
        assertTrue(children[1].bounds.left > children[0].bounds.right)
    }

    @Test
    fun wrapsFixedWidthChildrenOntoNextLine() {
        val root = document(CssStyle(direction = FlexDirection.ROW, wrap = FlexWrap.WRAP, width = px(220), height = px(200), gap = 10f)) {
            repeat(3) { element(CssStyle(width = px(100), height = px(50))) }
        }
        val children = FlexLayoutEngine().layout(root, 220f, 200f).children
        assertEquals(children[0].bounds.top, children[1].bounds.top)
        assertTrue(children[2].bounds.top > children[0].bounds.top)
    }
}
