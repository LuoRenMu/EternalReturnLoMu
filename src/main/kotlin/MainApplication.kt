package cn.luorenmu


import cn.luorenmu.api.qqBotRouting
import cn.luorenmu.api.resourcesRouting
import cn.luorenmu.api.templateRouting
import cn.luorenmu.common.util.BrowserPool
import cn.luorenmu.listen.GroupAtMessageCreateListen
import cn.luorenmu.render.PlayerPageRender
import cn.luorenmu.render.TierStatisticsNumberRender
import cn.luorenmu.service.QGBotService
import cn.luorenmu.service.ResourcesDownloadService
import cn.luorenmu.service.TemplateService
import freemarker.cache.ClassTemplateLoader
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.freemarker.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import love.forte.simbot.application.listeners
import love.forte.simbot.common.function.ConfigurerFunction
import love.forte.simbot.component.qguild.event.QGAtMessageCreateEvent
import love.forte.simbot.component.qguild.firstQQGuildBotManager
import love.forte.simbot.component.qguild.useQQGuild
import love.forte.simbot.core.application.launchSimpleApplication
import love.forte.simbot.event.ChatGroupMessageEvent
import love.forte.simbot.event.EventResult
import love.forte.simbot.event.listen
import love.forte.simbot.event.process
import love.forte.simbot.qguild.event.EventIntents
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import java.io.FileInputStream
import java.util.concurrent.Executors

/**
 *
 * @author LoMu
 * Date 2025/9/17 12:47
 *
 */
class MainApplication

private val json = Json.parseToJsonElement(FileInputStream("C:\\Users\\LoMu\\Desktop\\Game\\qgbot token").use {
    it.bufferedReader().readText()
})
public  val APP_ID = json.jsonObject["APP_ID"]!!.jsonPrimitive.content
private  val SECRET = json.jsonObject["SECRET"]!!.jsonPrimitive.content
private  val TOKEN = json.jsonObject["TOKEN"]!!.jsonPrimitive.content

public val apiKey = mutableMapOf("x-api-key" to json.jsonObject["API_KEY"]!!.jsonPrimitive.content)

const val SERVER_PORT = 8080

lateinit var simbotApplication: love.forte.simbot.application.Application

suspend fun main1(args: Array<String>) {
    BrowserPool.getBrowser()
    simbotApplication = launchSimbot()
    embeddedServer(Netty, port = SERVER_PORT, host = "0.0.0.0") {
        module()
    }.start(wait = true)
}


suspend fun launchSimbot(): love.forte.simbot.application.Application {
    val application = launchSimpleApplication {
        useQQGuild()
    }
    application.configure()

    return application
}


suspend fun love.forte.simbot.application.Application.configure() {
    val botManager = botManagers.firstQQGuildBotManager()
    val bot = botManager.register(
        appId = APP_ID,
        secret = SECRET,
        token = TOKEN,
    ) {
        botConfigure = ConfigurerFunction {
            intents += EventIntents.GroupAndC2CEvent.intents
            useSandboxServerUrl()
            disableWs = true
        }
        cacheConfig = null
    }
    bot.start()


    listeners {
        // 使用 listen 监听一个事件
        // 此处是一个标准库中通用的类型：聊天群消息事件

        listen<ChatGroupMessageEvent> { event ->
            GroupAtMessageCreateListen().handle(event)


            // 使用listen时必须返回一个EventResult类型的结果
            EventResult.empty()
        }

        // 使用 process 监听一个事件
        // 此处监听的是QQ机器人组件中的专属类型：文字子频道中的At消息事件
        process<QGAtMessageCreateEvent> { event ->
            println("ProcessEvent: $event")
        }
    }
}

fun Application.module() {
    configureRouting()
    configureInstall()
}


val appModule = module {
    single { TemplateService() }
    single { ResourcesDownloadService() }
    single { QGBotService() }
    single { PlayerPageRender() }
    single { TierStatisticsNumberRender() }
    single { Executors.newFixedThreadPool(10).asCoroutineDispatcher() }
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
        get("/") {
            call.respondText("Biu biu ~", ContentType.Text.Html)
            return@get
        }
        templateRouting()
        qqBotRouting()
        resourcesRouting()
    }
}