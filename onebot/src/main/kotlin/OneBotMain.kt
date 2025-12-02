package cn.luorenmu.onebot

import cn.luorenmu.Adapter
import cn.luorenmu.SERVER_PORT
import cn.luorenmu.command.CommandListenAllocator
import cn.luorenmu.command.entity.MessageSender
import cn.luorenmu.moduleCore
import io.ktor.http.Url
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import love.forte.simbot.application.Application
import love.forte.simbot.application.listeners
import love.forte.simbot.component.onebot.v11.core.bot.OneBotBotConfiguration
import love.forte.simbot.component.onebot.v11.core.bot.firstOneBotBotManager
import love.forte.simbot.component.onebot.v11.core.useOneBot11
import love.forte.simbot.core.application.launchSimpleApplication
import love.forte.simbot.event.ChatGroupMessageEvent
import love.forte.simbot.event.process
import java.util.UUID

/**
 *
 * @author LoMu
 * Date 2025/11/25 21:19
 *
 *
*/

private val commandListenAllocator = CommandListenAllocator()
suspend fun main() {
    val app = launchSimpleApplication {
        useOneBot11()
    }
    app.configure()
    embeddedServer(Netty, port = SERVER_PORT, host = "0.0.0.0") {
        moduleCore(Adapter.ONE_BOT)
    }.start(wait = true)
}

suspend fun Application.configure() {
    // 寻找、获得所需的BotManager
    val botManager = botManagers.firstOneBotBotManager()
    // 注册你所需的bot
    val bot = botManager.register(
        OneBotBotConfiguration().apply {
            // 这几个是必选属性
            /// 在OneBot组件中用于区分不同Bot的唯一ID， 建议可以直接使用QQ号。
            botUniqueId = UUID.randomUUID().toString()
            apiServerHost = Url("http://localhost:3000")
            eventServerHost = Url("ws://localhost:3001")
        }
    )
    listeners {
        process <ChatGroupMessageEvent> { event ->

            val reply = commandListenAllocator.call(
                MessageSender(
                    groupOpenId = event.id,
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
    bot.start()
}