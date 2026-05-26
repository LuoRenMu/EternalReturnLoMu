package cn.luorenmu.api

import cn.luorenmu.common.util.PathUtils
import io.ktor.server.http.content.*
import io.ktor.server.routing.*

/**
 *
 * @author LoMu
 * Date 2025/11/2 01:36
 */
fun Route.resourcesRouting() {
    staticFiles("/resources", PathUtils.resourcesPathResolve().toFile())
    staticResources("/static/css", "static/templates/css")
    staticResources("/static/js", "static/templates/js")
    staticResources("/static/images", "static/images")
}