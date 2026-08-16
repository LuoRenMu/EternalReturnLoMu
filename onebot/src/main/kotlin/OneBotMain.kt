package cn.luorenmu.onebot

import cn.luorenmu.Adapter
import cn.luorenmu.ConfigFile.config
import cn.luorenmu.command.CommandRouter
import cn.luorenmu.command.entity.MessageSender
import cn.luorenmu.moduleCore
import io.ktor.http.*
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
suspend fun main(args: Array<String>) {
    val adminPort = AdminServerPort.resolve(args)
    val app = launchSimpleApplication {
        useOneBot11()
    }
    app.configure()
    embeddedServer(Netty, port = adminPort, host = "0.0.0.0") {
        moduleCore(Adapter.ONE_BOT, adminPort)
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
            botUniqueId = UUID.randomUUID().toString()
            apiServerHost = Url(httpUrl)
            eventServerHost = Url(webSocketUrl)
        }
    )
    listeners {
        process<ChatGroupMessageEvent> { event ->
            val reply = commandRouter.call(
                MessageSender(
                    groupOpenId = event.content().id,
                    senderName = event.author().name,
                    senderOpenId = event.authorId,
                    message = event.messageContent.messages.toString(),
                    plainText = event.messageContent.plainText?.trim() ?: "",
                )
            )
            reply?.let { event.reply(it) }
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
