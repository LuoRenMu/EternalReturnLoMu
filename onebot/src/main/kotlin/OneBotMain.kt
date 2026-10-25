package cn.luorenmu.onebot

import cn.luorenmu.Adapter
import cn.luorenmu.ConfigFile.config
import cn.luorenmu.command.CommandRouter
import cn.luorenmu.command.RecentMessageGuard
import cn.luorenmu.command.entity.MessageSender
import cn.luorenmu.moduleCore
import cn.luorenmu.health.HealthCheck
import cn.luorenmu.health.HealthResult
import love.forte.simbot.component.onebot.v11.core.api.GetStatusApi
import io.ktor.http.*
import io.ktor.client.engine.cio.CIO
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import love.forte.simbot.application.Application
import love.forte.simbot.application.listeners
import love.forte.simbot.component.onebot.v11.core.bot.OneBotBotConfiguration
import love.forte.simbot.component.onebot.v11.core.bot.firstOneBotBotManager
import love.forte.simbot.component.onebot.v11.core.useOneBot11
import love.forte.simbot.core.application.launchSimpleApplication
import love.forte.simbot.event.ChatGroupMessageEvent
import love.forte.simbot.event.process
import java.net.ConnectException
import java.util.*

/**
 *
 * @author LoMu
 * Date 2025/11/25 21:19
 *
 *
 */

private val commandRouter = CommandRouter()
private val recentMessages = RecentMessageGuard()
suspend fun main(args: Array<String>) {
    val adminPort = AdminServerPort.resolve(args, config.port)
    val app = launchSimpleApplication {
        useOneBot11()
    }
    app.configure()
    embeddedServer(Netty, port = adminPort, host = "0.0.0.0") {
        moduleCore(Adapter.ONE_BOT, adminPort, healthChecks = listOf(HealthCheck("onebot", "OneBot 在线状态") {
            val bot = app.botManagers.firstOneBotBotManager().all().firstOrNull()
            if (bot == null || !bot.isActive) HealthResult(false, "OneBot 未连接") else {
                val status = bot.executeData(GetStatusApi.create())
                HealthResult(status.good && status.online != false, if (status.good && status.online != false) "正常" else "OneBot 离线/异常")
            }
        }))
    }.start(wait = true)
}

suspend fun Application.configure() {
    val httpUrl = config.other["one_bot_http"].orEmpty()
    val webSocketUrl = config.other["one_bot_ws"].orEmpty()
    if (httpUrl.isBlank() || webSocketUrl.isBlank()) {
        println("OneBot 地址未配置，已跳过机器人连接。请访问 / 完成配置后重启。")
        return
    }

    val botManager = botManagers.firstOneBotBotManager()
    val bot = botManager.register(
        OneBotBotConfiguration().apply {
            // Keep SDK HTTP/WebSocket engine selection independent from business HTTP dependencies.
            apiClientEngineFactory = CIO
            wsClientEngineFactory = CIO
            botUniqueId = UUID.randomUUID().toString()
            apiServerHost = Url(httpUrl)
            eventServerHost = Url(webSocketUrl)
        }
    )
    listeners {
        process<ChatGroupMessageEvent> { event ->
            if (!recentMessages.shouldProcess(event.messageContent.id.toString())) return@process
            val reply = commandRouter.call(
                MessageSender(
                    groupOpenId = event.content().id,
                    senderName = event.author().name,
                    senderOpenId = event.authorId,
                    message = event.messageContent.messages.toString(),
                    plainText = event.messageContent.plainText?.trim() ?: "",
                )
            )
            reply?.let { event.reply(it.toOneBotCompatibleMessage()) }
        }
    }

    // 启动你的bot
    try {
        bot.start()
    } catch (e: ConnectException) {
        println("================")
        println("请先启动OneBot服务")
        println("================")
    }

}
