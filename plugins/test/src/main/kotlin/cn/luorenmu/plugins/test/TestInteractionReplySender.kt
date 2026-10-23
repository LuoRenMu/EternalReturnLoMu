package cn.luorenmu.plugins.test

import love.forte.simbot.component.qguild.ExperimentalQGApi
import love.forte.simbot.component.qguild.bot.QGBot
import love.forte.simbot.component.qguild.message.MessageParsers
import love.forte.simbot.component.qguild.message.SendingMessageParser
import love.forte.simbot.message.Message
import love.forte.simbot.qguild.api.message.GroupAndC2CSendBody
import love.forte.simbot.qguild.api.message.group.GroupMessageSendApi

internal object TestInteractionReplySender {
    @OptIn(ExperimentalQGApi::class)
    suspend fun prepareBodies(bot: QGBot, groupId: String, eventId: String, reply: Message): List<GroupAndC2CSendBody> =
        MessageParsers.parseToGroupAndC2C(bot, reply, SendingMessageParser.GroupBuilderType.GROUP, groupId,
            { GroupAndC2CSendBody.create("", 0) }).onEachIndexed { index, body ->
                // Passive replies use the outer event ID, not the bare interaction ID or the original image msg_id.
                body.eventId = eventId
                body.msgId = null
                body.msgSeq = index + 1
            }

    suspend fun send(bot: QGBot, groupId: String, eventId: String, reply: Message) {
        for (body in prepareBodies(bot, groupId, eventId, reply)) {
            bot.executeData(GroupMessageSendApi.create(groupId, body))
        }
    }
}
