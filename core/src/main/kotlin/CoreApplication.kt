package cn.luorenmu


import cn.luorenmu.api.resourcesRouting
import cn.luorenmu.common.util.BrowserPool
import cn.luorenmu.service.EternalReturnRenderService
import cn.luorenmu.service.ResourcesDownloadService
import freemarker.cache.ClassTemplateLoader
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.*
import io.ktor.server.freemarker.*
import io.ktor.server.routing.*
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import java.io.File

/**
 *
 * @author LoMu
 * Date 2025/9/17 12:47
 *
 */
class CoreApplication

public val apiKey = mutableMapOf("x-api-key" to File("E:\\code\\Kotlin Code\\api-key.txt").readText())

var SERVER_PORT: Int = 8080
var HTTP_SERVER_URL = "http://127.0.0.1:${SERVER_PORT}"

enum class Adapter {
    ONE_BOT, QG_BOT
}

lateinit var currentAdapter: Adapter

private val log = KotlinLogging.logger {}
fun Application.moduleCore(adapter: Adapter) {
    currentAdapter = adapter
    configureRouting()
    configureInstall()
    log.info("正在启动 PlayWright")
    BrowserPool.getBrowser()
    log.info("PlayWright 已启动")
    environment.config.port.let {
        SERVER_PORT = it
    }
}


val appModule = module {

    single { ResourcesDownloadService() }
    single { EternalReturnRenderService() }
}

fun Application.configureInstall() {
    install(FreeMarker) {
        templateLoader = ClassTemplateLoader(this::class.java.classLoader, "static/templates")
    }

    install(Koin) {
        modules(appModule)
    }

}


fun Application.configureRouting() {
    routing {
        resourcesRouting()
    }
}