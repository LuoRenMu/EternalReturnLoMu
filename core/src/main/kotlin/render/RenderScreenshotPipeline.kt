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
}
