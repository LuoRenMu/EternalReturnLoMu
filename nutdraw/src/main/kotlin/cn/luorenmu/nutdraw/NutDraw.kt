package cn.luorenmu.nutdraw

import cn.luorenmu.nutdraw.template.ImageTemplate
import cn.luorenmu.nutdraw.template.TemplateManager
import java.nio.file.Path

/**
 * Host-owned generic rendering Implementation shared by independently reloadable command Modules.
 *
 * @author LoMu
 * Date 2026/8/16 15:30
 */
object NutDraw {
    private val templates = TemplateManager()

    suspend fun <T> render(template: ImageTemplate<T>, data: T, output: Path): Path =
        templates.render(template, data, output)
}
