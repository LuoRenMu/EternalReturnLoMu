package cn.luorenmu.nutdraw

import cn.luorenmu.nutdraw.css.CssStyle
import cn.luorenmu.nutdraw.dom.document
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class DomIdLookupTest {
    @Test
    fun `finds elements by id and nested shinobu style path`() {
        val root = document(CssStyle(), id = "root") {
            element(id = "body") {
                element(id = "rank") { text("RP", id = "chart") }
            }
        }
        assertEquals("rank", root.findById("rank")?.id)
        assertNotNull(root["body/rank/chart"])
    }
}
