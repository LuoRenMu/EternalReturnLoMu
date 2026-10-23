package cn.luorenmu.plugins.qqmedia

import love.forte.simbot.component.qguild.ExperimentalQGApi
import love.forte.simbot.component.qguild.bot.QGBot
import love.forte.simbot.component.qguild.message.MessageParsers
import love.forte.simbot.component.qguild.message.SendingMessageParser
import love.forte.simbot.message.Message
import love.forte.simbot.qguild.api.message.GroupAndC2CSendBody
import love.forte.simbot.qguild.api.message.group.GroupMessageSendApi

/** Active delivery through Simbot: upload images normally, omit passive reply IDs. */
object QQProactiveGroupSender {
    @OptIn(ExperimentalQGApi::class)
    suspend fun prepareBody(bot: QGBot, groupId: String, message: Message): GroupAndC2CSendBody {
        require(groupId.matches(Regex("[A-Fa-f0-9]{32}"))) { "Invalid group OpenID" }
        val bodies = MessageParsers.parseToGroupAndC2C(bot, message, SendingMessageParser.GroupBuilderType.GROUP, groupId,
            { GroupAndC2CSendBody.create("", 0) })
        require(bodies.size == 1) { "Active notification must be a single message" }
        return bodies.single().apply { msgId = null; eventId = null }
    }

    @OptIn(ExperimentalQGApi::class)
    suspend fun send(bot: QGBot, groupId: String, message: Message) {
        bot.executeData(GroupMessageSendApi.create(groupId, prepareBody(bot, groupId, message)))
    }
}
