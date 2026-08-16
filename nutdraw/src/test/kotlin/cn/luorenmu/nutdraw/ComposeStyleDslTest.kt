package cn.luorenmu.nutdraw

import cn.luorenmu.nutdraw.css.CssStyle
import cn.luorenmu.nutdraw.css.FlexDirection
import cn.luorenmu.nutdraw.dom.Document
import cn.luorenmu.nutdraw.dom.NutElement
import cn.luorenmu.nutdraw.dom.NutImage
import cn.luorenmu.nutdraw.dom.NutText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 *
 * @author LoMu
 * Date 2026/8/16 15:30
 */
class ComposeStyleDslTest {
    @Test
    fun `row and column express layout direction through their names`() {
        val root = Document(CssStyle(), id = "root") {
            Row(CssStyle(direction = FlexDirection.COLUMN), id = "row") {
                Column(CssStyle(direction = FlexDirection.ROW), id = "column") {
                    Text("Player")
                    Image("avatar.png")
                }
            }
        }

        val row = assertIs<NutElement>(root.findById("row"))
        val column = assertIs<NutElement>(root.findById("column"))
        assertEquals(FlexDirection.ROW, row.style.direction)
        assertEquals(FlexDirection.COLUMN, column.style.direction)
        assertIs<NutText>(column.children[0])
        assertIs<NutImage>(column.children[1])
    }
}
