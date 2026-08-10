package cn.luorenmu.api

import cn.luorenmu.render.FreemarkerRenderer
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class AdminDashboardTemplateTest {

    @Test
    fun rendersDashboard() {
        val html = FreemarkerRenderer.render("admin_dashboard.ftl", emptyMap<String, String>())

        assertContains(html, "LoMu Bot 指令测试")
        assertContains(html, "/api/admin/test-command")
        assertFalse(html.contains("/api/admin/command-usages"))
    }
}
