package cn.luorenmu.nutdraw.template

import cn.luorenmu.nutdraw.render.SkiaDocumentRenderer
import java.nio.file.Path

/**
 * Build -> resolve resources -> layout -> draw lifecycle, matching Shinobu's Template boundary.
 *
 * @author LoMu
 * Date 2026/8/16 15:30
 */
class TemplateManager(private val renderer: SkiaDocumentRenderer = SkiaDocumentRenderer()) {
    suspend fun <T> render(template: ImageTemplate<T>, data: T, output: Path): Path {
        val document = template.build(data)
        return renderer.render(document.root, output, document.width, document.height)
    }
}
