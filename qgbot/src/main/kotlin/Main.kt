package cn.luorenmu.qqbot

import cn.luorenmu.Adapter
import cn.luorenmu.ConfigFile
import cn.luorenmu.api.qqBotRouting
import cn.luorenmu.qqbot.listen.GroupAtMessageCreateListen
import cn.luorenmu.moduleCore
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import love.forte.simbot.application.listeners
import love.forte.simbot.common.function.ConfigurerFunction
import love.forte.simbot.component.qguild.firstQQGuildBotManager
import love.forte.simbot.component.qguild.useQQGuild
import love.forte.simbot.core.application.launchSimpleApplication
import love.forte.simbot.event.ChatGroupMessageEvent
import love.forte.simbot.event.EventResult
import love.forte.simbot.event.listen
import love.forte.simbot.qguild.event.EventIntents

/**
 *
 * @author LoMu
 * Date 2025/11/18 19:25
 */



const val SERVER_PORT = 8080

lateinit var simbotApplication: love.forte.simbot.application.Application

suspend fun main(args: Array<String>) {
    simbotApplication = launchSimbot()
    embeddedServer(Netty, port = SERVER_PORT, host = "0.0.0.0") {
        module()
        moduleCore(Adapter.QG_BOT)
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
        appId = ConfigFile.config.other["app_id"]!!,
        secret = ConfigFile.config.other["secret"]!!,
        token = ConfigFile.config.other["token"]!!,
    ) {
        botConfigure = ConfigurerFunction {
            intents += EventIntents.GroupAndC2CEvent.intents
            disableWs = true
        }
        cacheConfig = null
    }
    bot.start()
    val groupAtMessageCreateListen = GroupAtMessageCreateListen()
    listeners {
        listen<ChatGroupMessageEvent> { event ->
            groupAtMessageCreateListen.handle(event)
            EventResult.empty()
        }
    }
}

fun Application.module() {
    configureRouting()
}


fun Application.configureRouting() {
    routing {
        get("/") {
            call.respondText("Biu biu ~", ContentType.Text.Html)
            return@get
        }
        qqBotRouting()
    }
}