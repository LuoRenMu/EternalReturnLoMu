package cn.luorenmu
import cn.luorenmu.ai.AIConfig
import cn.luorenmu.ai.KoogLLMClient
import cn.luorenmu.ai.NewsClassifier
import cn.luorenmu.api.resourcesRouting
import cn.luorenmu.common.util.BrowserPool
import cn.luorenmu.common.util.DatabaseManager
import cn.luorenmu.common.util.PathUtils
import cn.luorenmu.request.ApiKeyConfig
import cn.luorenmu.repository.PlayerAliasRepository
import cn.luorenmu.repository.StatisticsRepository
import cn.luorenmu.service.CharacterDetailCollector
import cn.luorenmu.service.CharacterStatsCollector
import cn.luorenmu.service.PlayerRenderAssembler
import cn.luorenmu.service.TierStatisticsCollector
import cn.luorenmu.service.ResourcesDownloadService
import cn.luorenmu.task.EternalReturnNewsTask
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

var SERVER_PORT: Int = ConfigFile.config.port
var HTTP_SERVER_URL = "http://127.0.0.1:${SERVER_PORT}"

enum class Adapter {
    ONE_BOT, QG_BOT
}

lateinit var currentAdapter: Adapter

private val logger = KotlinLogging.logger {}
fun Application.moduleCore(adapter: Adapter) {
    currentAdapter = adapter
    ApiKeyConfig.apiKeyMap = mutableMapOf("x-api-key" to ConfigFile.config.apiKey)
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
    single { PlayerRenderAssembler() }
    single { CharacterStatsCollector() }
    single { CharacterDetailCollector(get()) }
    single { TierStatisticsCollector() }
    single { KoogLLMClient(AIConfig(ConfigFile.config.ai.apiKey, ConfigFile.config.ai.model, ConfigFile.config.ai.baseUrl)) }
    single { NewsClassifier(get()) }
    single { EternalReturnNewsTask(get()) }
    single { DatabaseManager() }
    single { StatisticsRepository(get()) }
    single { PlayerAliasRepository(get()) }
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
        println(file)
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
            println(file.toPath())
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
        var postgres: PostgresConfig = PostgresConfig(),
        var ai: AIConfig = AIConfig(),
    ) {
        @Serializable
        data class BrowserConfig(
            var headless: Boolean = false,
            var pool: Int = 1,
        )

        @Serializable
        data class PostgresConfig(
            var enabled: Boolean = true,
            var host: String = "localhost",
            var port: Int = 5432,
            var database: String = "bot_db",
            var user: String = "postgres",
            var password: String = "postgres",
            var schema: String = "public",
        )

        @Serializable
        data class AIConfig(
            var apiKey: String = "",
            var model: String = "deepseek-chat",
            var baseUrl: String = "https://api.deepseek.com",
        )
    }
}