package cn.luorenmu.plugins.qqmedia

import cn.luorenmu.exception.MessageReplyException
import love.forte.simbot.application.Application
import love.forte.simbot.component.qguild.bot.QGBot
import love.forte.simbot.component.qguild.filterIsQQGuildBotManagers

/** Read-only bridge to the host's existing SDK application; avoids a plugin -> qgbot dependency cycle. */
object SimbotQQRuntime {
    fun applicationOrNull(): Application? = runCatching {
        Class.forName("cn.luorenmu.qqbot.MainKt", true, QGBot::class.java.classLoader)
            .getMethod("getSimbotApplication").invoke(null) as Application
    }.getOrNull()

    fun requireBot(): QGBot = applicationOrNull()?.botManagers?.filterIsQQGuildBotManagers()
        ?.firstNotNullOfOrNull { it.all().firstOrNull() }
        ?: throw MessageReplyException("未找到运行中的 Simbot QQ 机器人")
}
