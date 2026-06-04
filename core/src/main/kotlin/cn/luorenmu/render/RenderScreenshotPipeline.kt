package cn.luorenmu.render

import cn.luorenmu.common.util.BrowserPool
import cn.luorenmu.common.util.PathUtils
import java.nio.file.Path
import java.util.*

/**
 * @author LoMu
 * Date 2026/5/23
 *
 * 统一的「FreeMarker 渲染 → 截图」管线，消除各命令中的重复样板代码。
 */
object RenderScreenshotPipeline {

    /**
     * 渲染模板写入临时文件后截图（文件模式）。
     */
    fun renderAndScreenshot(
        template: String,
        data: Any,
        outputPath: Path,
        selector: String,
    ) {
        val html = FreemarkerRenderer.render(template, data)
        val tmpFile = PathUtils.resourcesPathResolve("render", "tmp", "${UUID.randomUUID()}.html")
        tmpFile.parent?.toFile()?.mkdirs()
        tmpFile.toFile().writeText(html)
        BrowserPool.getBrowser().screenshotSelector(tmpFile.toString(), outputPath, selector)
        tmpFile.toFile().delete()
    }

    /**
     * 渲染模板后直接截图（内容模式，不落盘）。
     */
    fun renderContentAndScreenshot(
        template: String,
        data: Any,
        outputPath: Path,
        selector: String,
    ) {
        val html = FreemarkerRenderer.render(template, data)
        BrowserPool.getBrowser().screenshotContentSelector(html, outputPath, selector)
    }

    /**
     * 渲染模板后用 setContent 注入截图，支持等待 JS 表达式（如 Chart.js 渲染完成）。
     * 比文件模式少一次 file:// 导航，适合含 JS 的模板。
     *
     * @param readyExpression 截图前等待的 JS 条件，null 则不等待
     */
    fun renderHtmlAndScreenshot(
        template: String,
        data: Any,
        outputPath: Path,
        selector: String,
        readyExpression: String? = null,
    ) {
        val html = FreemarkerRenderer.render(template, data)
        BrowserPool.getBrowser().screenshotHtmlSelector(html, outputPath, selector, readyExpression)
    }
}
