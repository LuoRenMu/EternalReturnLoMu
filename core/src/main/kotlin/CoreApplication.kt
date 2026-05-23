package cn.luorenmu


import cn.luorenmu.api.resourcesRouting
import cn.luorenmu.common.util.BrowserPool
import cn.luorenmu.common.util.MongoDBManager
import cn.luorenmu.common.util.PathUtils
import cn.luorenmu.repository.StatisticsRepository
import cn.luorenmu.service.EternalReturnRenderService
import cn.luorenmu.service.ResourcesDownloadService
import freemarker.cache.ClassTemplateLoader
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.*
import io.ktor.server.freemarker.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import kotlin.system.exitProcess

/**
 *
 * @author LoMu
 * Date 2025/9/17 12:47
 *
 */
class CoreApplication

val apiKey = mutableMapOf("x-api-key" to ConfigFile.config.apiKey)

var SERVER_PORT: Int = ConfigFile.config.port
var HTTP_SERVER_URL = "http://127.0.0.1:${SERVER_PORT}"

enum class Adapter {
    ONE_BOT, QG_BOT
}

lateinit var currentAdapter: Adapter

private val logger = KotlinLogging.logger {}
fun Application.moduleCore(adapter: Adapter) {
    currentAdapter = adapter
    configureRouting()
    configureInstall()
    logger.info { "正在启动 PlayWright" }
    BrowserPool.getBrowser()
    logger.info { "PlayWright 已启动" }
    environment.config.port.let {
        SERVER_PORT = it
    }
}


val appModule = module {

    single { ResourcesDownloadService() }
    single { EternalReturnRenderService() }
    single { MongoDBManager() }
    single { StatisticsRepository(get()) }
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


object ConfigFile {
    val config: BotConfig = initConfig()
    fun initConfig(): BotConfig {
        val file = PathUtils.pathResolve(paths = arrayOf("config.json")).toFile()
        if (!file.exists()) {
            file.createNewFile()
            val json = Json {
                prettyPrint = true
                encodeDefaults = true
            }
            val botConfig = BotConfig()
            file.writeText(
                json.encodeToString(botConfig)
            )
            println("初次运行, 请填写 config.json 文件.(first run, please fill in config.json file)")
            exitProcess(0)
        }
        val json = Json.decodeFromString<BotConfig>(file.readText())
        return json
    }


    @Serializable
    data class BotConfig(
        var port: Int = 8080,
        var apiKey: String = "非必要",
        var other: Map<String, String> = mapOf(),
        var browser: BrowserConfig = BrowserConfig(),
        var mongo: MongoConfig = MongoConfig(),
    ) {
        @Serializable
        data class BrowserConfig(
            var headless: Boolean = false,
            var pool: Int = 1,
        )

        @Serializable
        data class MongoConfig(
            var enabled: Boolean = true,
            var connectionString: String = "mongodb://192.168.1.104:27017",
            var database: String = "bot_db",
        )
    }
}