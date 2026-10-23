package cn.luorenmu.plugins.qqmedia

import love.forte.simbot.component.qguild.ExperimentalQGApi
import love.forte.simbot.component.qguild.bot.QGBot
import love.forte.simbot.component.qguild.message.MessageParsers
import love.forte.simbot.component.qguild.message.SendingMessageParser
import love.forte.simbot.message.Message
import love.forte.simbot.qguild.api.message.GroupAndC2CSendBody
import love.forte.simbot.qguild.api.message.group.GroupMessageSendApi

/** Use the SDK's normal text/image/Markdown parsing, but reply to the outer interaction event ID. */
object QQGroupInteractionReplySender {
    @OptIn(ExperimentalQGApi::class)
    suspend fun prepareBodies(bot: QGBot, groupId: String, eventId: String, reply: Message): List<GroupAndC2CSendBody> =
        MessageParsers.parseToGroupAndC2C(bot, reply, SendingMessageParser.GroupBuilderType.GROUP, groupId,
            { GroupAndC2CSendBody.create("", 0) }).onEachIndexed { index, body ->
                body.eventId = eventId
                body.msgId = null
                body.msgSeq = index + 1
            }

    @OptIn(ExperimentalQGApi::class)
    suspend fun send(bot: QGBot, groupId: String, eventId: String, reply: Message) {
        for (body in prepareBodies(bot, groupId, eventId, reply))
            bot.executeData(GroupMessageSendApi.create(groupId, body))
    }
}
