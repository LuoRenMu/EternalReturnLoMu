package cn.luorenmu
import cn.luorenmu.ai.AIConfig
import cn.luorenmu.ai.KoogLLMClient
import cn.luorenmu.ai.news.NewsClassifier
import cn.luorenmu.api.AdminAccessToken
import cn.luorenmu.api.resourcesRouting
import cn.luorenmu.common.util.DatabaseManager
import cn.luorenmu.common.util.DatabaseBackend
import cn.luorenmu.common.util.PathUtils
import cn.luorenmu.command.plugin.CommandPlugins
import cn.luorenmu.request.ApiKeyConfig
import cn.luorenmu.repository.NewsRepository
import cn.luorenmu.repository.ExceptionRepository
import cn.luorenmu.repository.PlayerAliasRepository
import cn.luorenmu.repository.StatisticsRepository
import cn.luorenmu.service.AdminConfigService
import cn.luorenmu.service.AdminDatabaseService
import cn.luorenmu.service.AdminSystemService
import cn.luorenmu.service.ResourcesDownloadService
import cn.luorenmu.task.EternalReturnNewsTask
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.*
import io.ktor.server.freemarker.FreeMarker
import io.ktor.server.routing.*
import freemarker.cache.ClassTemplateLoader
import freemarker.template.Configuration
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.dsl.module
import org.koin.ktor.ext.getKoin
import org.koin.ktor.plugin.Koin
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption

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
fun Application.moduleCore(adapter: Adapter, serverPort: Int = ConfigFile.config.port) {
    currentAdapter = adapter
    SERVER_PORT = serverPort
    HTTP_SERVER_URL = "http://127.0.0.1:$serverPort"
    AdminAccessToken.configure(ConfigFile.config.adminToken)
    println("管理后台访问令牌已从 config.json 加载")
    println("管理后台地址: $HTTP_SERVER_URL/")
    CommandPlugins.initialize(adapter, PathUtils.pathResolve(paths = arrayOf("plugins")))
    ApiKeyConfig.apiKeyMap = mutableMapOf("x-api-key" to ConfigFile.config.apiKey)
    configureRouting()
    configureInstall()
    getKoin()
        .get<DatabaseManager>()
        .initialize()
    // 启动新闻定时任务
    getKoin()
        .get<EternalReturnNewsTask>()
        .start()
    logger.info { "新闻定时任务已启动" }
}


val appModule = module {

    single { ResourcesDownloadService() }
    single { KoogLLMClient(AIConfig(ConfigFile.config.ai.apiKey, ConfigFile.config.ai.model, ConfigFile.config.ai.baseUrl)) }
    single { NewsClassifier(get()) }
    single { NewsRepository(get()) }
    single { EternalReturnNewsTask(get(), get()) }
    single {
        DatabaseManager(
            if (currentAdapter == Adapter.ONE_BOT) DatabaseBackend.SQLITE else DatabaseBackend.POSTGRESQL
        )
    }
    single { StatisticsRepository(get()) }
    single { ExceptionRepository(get()) }
    single { PlayerAliasRepository(get()) }
    single { AdminConfigService(get()) }
    single { AdminDatabaseService(get()) }
    single { AdminSystemService(get()) }
}


fun Application.configureInstall() {
    install(Koin) {
        modules(appModule)
    }
    install(FreeMarker) {
        configureAdminTemplates()
    }

}

internal fun Configuration.configureAdminTemplates() {
    defaultEncoding = StandardCharsets.UTF_8.name()
    outputEncoding = StandardCharsets.UTF_8.name()
    templateLoader = ClassTemplateLoader(CoreApplication::class.java.classLoader, "templates")
}

fun Application.configureRouting() {
    routing {
        resourcesRouting()
    }
}


object ConfigFile {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    @Volatile
    var config: BotConfig = initConfig()
        private set

    fun initConfig(): BotConfig {
        val file = PathUtils.pathResolve(paths = arrayOf("config.json")).toFile()
        println(file)
        if (!file.exists()) {
            val botConfig = BotConfig()
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(botConfig))
            println("已创建默认 config.json，请手动编辑后重启服务。")
            return botConfig
        }
        return json.decodeFromString<BotConfig>(file.readText())
    }

    @Synchronized
    fun save(next: BotConfig) {
        require(next.adminToken.isNotBlank()) { "config.json 中的 adminToken 不能为空" }
        val file = PathUtils.pathResolve(paths = arrayOf("config.json"))
        val temporary = file.resolveSibling("${file.fileName}.tmp")
        Files.writeString(temporary, json.encodeToString(next))
        runCatching {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }.getOrElse {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING)
        }
        config = next
        AdminAccessToken.configure(next.adminToken)
        ApiKeyConfig.apiKeyMap = mutableMapOf("x-api-key" to next.apiKey)
    }


    @Serializable
    data class BotConfig(
        var port: Int = 8080,
        var adminToken: String = "lomu-admin",
        var apiKey: String = "非必要",
        var other: Map<String, String> = mapOf(),
        var postgres: PostgresConfig = PostgresConfig(),
        var ai: AIConfig = AIConfig(),
    ) {
        @Serializable
        data class PostgresConfig(
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
            var model: String = "Qwen/Qwen3-VL-30B-A3B-Thinking",
            var baseUrl: String = "https://api.siliconflow.cn/v1",
        )
    }
}
