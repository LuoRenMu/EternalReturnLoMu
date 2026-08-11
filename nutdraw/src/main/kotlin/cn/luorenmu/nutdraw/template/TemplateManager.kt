package cn.luorenmu.nutdraw.template

import cn.luorenmu.nutdraw.render.SkiaDocumentRenderer
import java.nio.file.Path

/** Build -> resolve resources -> layout -> draw lifecycle, matching Shinobu's Template boundary. */
class TemplateManager(private val renderer: SkiaDocumentRenderer = SkiaDocumentRenderer()) {
    suspend fun <T> render(template: ImageTemplate<T>, data: T, output: Path): Path {
        val document = template.build(data)
        return renderer.render(document.root, output, document.width, document.height)
    }
}
