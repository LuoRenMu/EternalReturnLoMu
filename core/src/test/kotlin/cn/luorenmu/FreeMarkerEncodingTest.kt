package cn.luorenmu

import freemarker.template.Configuration
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 *
 * @author LoMu
 * Date 2026/8/16 14:11
 */
class FreeMarkerEncodingTest {
    @Test
    fun `admin templates always use utf8 instead of the operating system encoding`() {
        val configuration = Configuration(Configuration.DEFAULT_INCOMPATIBLE_IMPROVEMENTS).apply {
            defaultEncoding = "GBK"
            outputEncoding = "GBK"
            configureAdminTemplates()
        }

        assertEquals("UTF-8", configuration.defaultEncoding)
        assertEquals("UTF-8", configuration.outputEncoding)
    }
}
