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
    adminRouting()
    staticFiles("/resources", PathUtils.resourcesPathResolve().toFile())
    staticResources("/static", "static")
}
