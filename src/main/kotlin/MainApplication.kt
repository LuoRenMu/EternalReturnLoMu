package cn.luorenmu


import cn.luorenmu.api.qqBotRouting
import cn.luorenmu.listen.GroupAtMessageCreateListen
import cn.luorenmu.service.QGBotService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import love.forte.simbot.application.listeners
import love.forte.simbot.common.function.ConfigurerFunction
import love.forte.simbot.component.qguild.event.QGAtMessageCreateEvent
import love.forte.simbot.component.qguild.event.QGGroupAtMessageCreateEvent
import love.forte.simbot.component.qguild.firstQQGuildBotManager
import love.forte.simbot.component.qguild.useQQGuild
import love.forte.simbot.core.application.launchSimpleApplication
import love.forte.simbot.event.ChatGroupMessageEvent
import love.forte.simbot.event.EventResult
import love.forte.simbot.event.listen
import love.forte.simbot.event.process
import love.forte.simbot.qguild.event.EventIntents
import kotlin.reflect.full.isSubclassOf

/**
 *
 * @author LoMu
 * Date 2025/9/17 12:47
 *
 */
class MainApplication



lateinit var simbotApplication: love.forte.simbot.application.Application

suspend fun main(args: Array<String>) {
    simbotApplication = launchSimbot()
    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
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
}


fun Application.configureRouting() {
    val qgBotService = QGBotService()
    routing {
        get("/") {
            call.respondText("Hello World!", ContentType.Text.Html)
            return@get
        }
        qqBotRouting(qgBotService)
    }
}