package cn.luorenmu.qqbot

import cn.luorenmu.Adapter
import cn.luorenmu.ConfigFile
import cn.luorenmu.api.qqBotRouting
import cn.luorenmu.qqbot.listen.GroupAtMessageCreateListen
import cn.luorenmu.moduleCore
import cn.luorenmu.qqbot.health.qqHealthCheck
import cn.luorenmu.qqbot.health.QQAccessTokenGuard
import cn.luorenmu.qqbot.health.QQTokenLease
import cn.luorenmu.qqbot.health.installQQTokenOverride
import cn.luorenmu.qqbot.menu.SimbotQQMenuPanelClient
import cn.luorenmu.qqbot.menu.shouldRetryQQApiRequest
import cn.luorenmu.qqbot.menu.shouldRetryQQApiException
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.engine.cio.CIO
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
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
import love.forte.simbot.qguild.api.app.GetAppAccessTokenApi
import love.forte.simbot.qguild.api.requestData

/**
 *
 * @author LoMu
 * Date 2025/11/18 19:25
 */



lateinit var simbotApplication: love.forte.simbot.application.Application
private var tokenGuard: QQAccessTokenGuard? = null

suspend fun main(args: Array<String>) {
    simbotApplication = launchSimbot()
    embeddedServer(Netty, port = ConfigFile.config.port, host = "0.0.0.0") {
        module()
        moduleCore(Adapter.QG_BOT, healthChecks = listOf(qqHealthCheck(simbotApplication)),
            qqMenuPanelClient = SimbotQQMenuPanelClient {
                simbotApplication.botManagers.firstQQGuildBotManager().all().firstOrNull()
            })
        environment.monitor.subscribe(ApplicationStopping) { tokenGuard?.stop() }
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
    val appId = ConfigFile.config.other["app_id"].orEmpty()
    val secret = ConfigFile.config.other["secret"].orEmpty()
    if (appId.isBlank() || secret.isBlank()) {
        println("QQ Guild 凭据未配置，已跳过机器人连接。请访问 / 完成配置后重启。")
        return
    }

    val botManager = botManagers.firstQQGuildBotManager()
    val bot = botManager.register(
        appId = appId,
        secret = secret,
        token = ConfigFile.config.other["token"] ?: "",
    ) {
        botConfigure = ConfigurerFunction {
            // Adding a business HTTP engine must not silently change the SDK's existing transports.
            apiClientEngineFactory = CIO
            wsClientEngineFactory = CIO
            intents += EventIntents.GroupAndC2CEvent.intents
            intents += EventIntents.Interaction.intents
            disableWs = true
            apiHttpRequestTimeoutMillis = 60_000
            apiHttpConnectTimeoutMillis = 10_000
            apiHttpSocketTimeoutMillis = 30_000
            apiClientAdditionalConfiguration {
                install(HttpRequestRetry) {
                    maxRetries = 3
                    retryIf { request, response ->
                        shouldRetryQQApiRequest(request.method, request.url.encodedPath, response.status.value)
                    }
                    retryOnExceptionIf { request, cause ->
                        shouldRetryQQApiException(request.method, request.url.build().encodedPath, cause)
                    }
                }
            }
        }
        cacheConfig = null
    }
    val tokenApi = GetAppAccessTokenApi.create(appId, secret)
    val guard = QQAccessTokenGuard(fetch = {
        val lease = tokenApi.requestData(client = bot.source.apiClient, token = null, server = null, appId = null)
        QQTokenLease(lease.accessToken, lease.expiresIn)
    })
    installQQTokenOverride(bot.source.apiClient, bot.source.apiServer.host, guard::currentToken)
    bot.start()
    tokenGuard = guard
    guard.start(bot.source)

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
        qqBotRouting()
    }
}
