package cn.luorenmu.render

import freemarker.cache.ClassTemplateLoader
import freemarker.template.Configuration
import java.io.StringWriter

/**
 *
 * @author LoMu
 * Date 2025/12/4 15:01
 */
object FreemarkerRenderer {
    private val freeMarkerCfg = Configuration(Configuration.VERSION_2_3_32).apply {
        templateLoader = ClassTemplateLoader(ClassLoader.getSystemClassLoader(), "/static/templates")
        defaultEncoding = "UTF-8"
        logTemplateExceptions = false
        wrapUncheckedExceptions = true
    }

    fun render(template: String, data: Any): String {
        val writer = StringWriter()
        val tpl = freeMarkerCfg.getTemplate(template)
        tpl.process(data, writer)
        return writer.toString()
    }
}