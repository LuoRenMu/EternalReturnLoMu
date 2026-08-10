package cn.luorenmu.api

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class AdminDashboardPageTest {
    @Test
    fun dashboardRemainsInteractiveStaticHtml() {
        val html = checkNotNull(javaClass.getResource("/static/admin_dashboard.html")).readText()
        assertContains(html, "LoMu Bot")
        assertContains(html, "/api/admin/test-command")
        assertFalse(html.contains("<#"))
    }
}
