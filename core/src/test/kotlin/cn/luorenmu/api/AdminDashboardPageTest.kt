package cn.luorenmu.api

import freemarker.cache.ClassTemplateLoader
import freemarker.template.Configuration
import cn.luorenmu.service.AdminSystemView
import java.io.StringWriter
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 *
 * @author LoMu
 * Date 2026/8/16 15:30
 */
class AdminDashboardPageTest {
    @Test
    fun dashboardUsesFreemarkerHtmxAlpineAndStandaloneCss() {
        val template = checkNotNull(javaClass.getResource("/templates/admin/dashboard.ftl")).readText()
        val loginTemplate = checkNotNull(javaClass.getResource("/templates/admin/login.ftl")).readText()
        val fragment = checkNotNull(javaClass.getResource("/templates/admin/fragments/system.ftl")).readText()
        val script = checkNotNull(javaClass.getResource("/static/admin/admin.js")).readText()
        val stylesheet = checkNotNull(javaClass.getResource("/static/admin/admin.css")).readText()

        assertContains(template, "href=\"/static/admin/admin.css\"")
        assertFalse(template.contains("@tailwindcss/browser"))
        assertContains(template, "<body class=\"relative isolate min-h-screen bg-transparent")
        assertContains(template, "<aside class=\"overflow-x-hidden")
        assertContains(template, "<nav class=\"mt-5 grid grid-cols-2")
        assertFalse(template.contains("overflow-x-auto"))
        assertContains(stylesheet, ".panel")
        assertContains(stylesheet, "--color-lomu-500")
        assertFalse(stylesheet.contains("tailwindcss", ignoreCase = true))
        assertContains(template, "htmx.min.js")
        assertContains(template, "alpinejs")
        assertContains(template, "x-data=\"adminDashboard\"")
        assertContains(template, "hx-get=\"/admin/fragments/system\"")
        assertContains(template, "hx-trigger=\"load, every 15s")
        assertContains(template, "${'$'}{backgroundImageUrl?html}")
        assertContains(template, "opacity:.72")
        assertContains(template, "background:rgb(255 247 251/.20)")
        assertContains(template, "pointer-events:none")
        assertFalse(template.contains("filter:blur"))
        assertContains(template, "rgb(245 143 186/.84)")
        assertContains(template, ".panel, .stat-card { background:rgb(255 255 255/.52)")
        assertContains(template, ".bg-lomu-50, .bg-white\\/80")
        assertContains(template, ".control { background:rgb(255 255 255/.58)")
        assertContains(template, ".button { background:rgb(255 255 255/.48)")
        assertContains(template, ".nav-button:hover, .nav-button-active")
        assertContains(fragment, "${'$'}{runtime.uptime?html}")
        assertContains(fragment, "data-runtime-uptime")
        assertContains(fragment, "data-server-time-millis")
        assertContains(script, "/api/admin/config")
        assertContains(script, "configState: config.databaseBackend")
        assertContains(template, "当前加载数据库")
        assertFalse(template.contains("配置中心"))
        assertFalse(template.contains("configForm"))
        assertFalse(script.contains("loadConfig"))
        assertFalse(script.contains("saveConfig"))
        assertEquals(1, Regex("/api/admin/config").findAll(script).count())
        assertContains(script, "/api/admin/database/tables")
        assertContains(script, "/api/admin/test-command")
        assertContains(script, "/api/admin/commands")
        assertContains(script, "selectedCommandInfo()")
        assertContains(script, "openImagePreview(url)")
        assertContains(template, "name=\"arguments\"")
        assertContains(template, "@click=\"openImagePreview(element.imageUrl)\"")
        assertContains(template, "@keydown.escape.window=\"previewImageUrl = null\"")
        assertContains(script, "/api/admin/exceptions?limit=100")
        assertContains(template, "view === 'exceptions'")
        assertContains(template, "view === 'about'")
        assertContains(template, "关于 EternalReturnLoMu")
        assertContains(template, "href=\"https://github.com/LuoRenMu/EternalReturnLoMu\"")
        assertContains(template, "rel=\"noopener noreferrer\"")
        assertContains(template, "654087758")
        assertContains(template, "查看详细追溯")
        assertContains(template, "item.stackTrace")
        assertFalse(script.contains("X-Admin-Token"))
        assertFalse(script.contains("sessionStorage"))
        assertContains(template, "令牌已验证")
        assertContains(loginTemplate, "action=\"/admin/login\"")
        assertContains(loginTemplate, "name=\"token\"")
        assertContains(loginTemplate, "config.json 中配置的访问令牌")
        assertContains(loginTemplate, "button-primary")
        assertContains(loginTemplate, "${'$'}{backgroundImageUrl?html}")
        assertContains(script, "setInterval(render, 1000)")
        assertContains(script, "htmx:afterSwap")
        assertEquals("https://img.cdn1.vip/i/6a8171cba817b_1786868171.webp", ADMIN_BACKGROUND_IMAGE)
    }

    @Test
    fun freemarkerTemplatesRenderWithDashboardModels() {
        val configuration = Configuration(Configuration.DEFAULT_INCOMPATIBLE_IMPROVEMENTS).apply {
            templateLoader = ClassTemplateLoader(javaClass.classLoader, "templates")
            defaultEncoding = "UTF-8"
        }

        val dashboard = StringWriter().also { output ->
            configuration.getTemplate("admin/dashboard.ftl").process(
                mapOf(
                    "pageTitle" to "LoMu Control Center",
                    "backgroundImageUrl" to "https://example.com/background.jpg",
                ),
                output,
            )
        }.toString()
        assertContains(dashboard, "LoMu Control Center")
        assertContains(dashboard, "https://example.com/background.jpg")

        val login = StringWriter().also { output ->
            configuration.getTemplate("admin/login.ftl").process(
                mapOf(
                    "pageTitle" to "LoMu Control Center",
                    "backgroundImageUrl" to "https://example.com/background.jpg",
                    "error" to "令牌无效",
                ),
                output,
            )
        }.toString()
        assertContains(login, "欢迎回来喵！")
        assertContains(login, "令牌无效")

        val fragment = StringWriter().also { output ->
            configuration.getTemplate("admin/fragments/system.ftl").process(
                mapOf(
                    "runtime" to AdminSystemView(
                        startedAt = "2026-08-11T16:57:57+08:00",
                        serverTime = "2026-08-11T18:00:00+08:00",
                        uptimeMillis = 3_723_000,
                        osName = "Windows",
                        osVersion = "11",
                        osArch = "amd64",
                        processors = 16,
                        javaVersion = "17",
                        javaVendor = "OpenJDK",
                        jvmMemoryUsedBytes = 256L * 1_024 * 1_024,
                        jvmMemoryMaxBytes = 4L * 1_024 * 1_024 * 1_024,
                        systemMemoryTotalBytes = 32L * 1_024 * 1_024 * 1_024,
                        systemMemoryFreeBytes = 20L * 1_024 * 1_024 * 1_024,
                        databaseBackend = "SQLite",
                        adapter = "ONE_BOT",
                        runtimePort = 5752,
                    ).toFragmentView()
                ),
                output,
            )
        }.toString()
        assertContains(fragment, "运行状态")
        assertContains(fragment, "ONE_BOT · SQLite · :5752")
    }
}
