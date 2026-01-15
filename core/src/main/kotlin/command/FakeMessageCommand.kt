package cn.luorenmu.command

import cn.luorenmu.Adapter
import cn.luorenmu.command.entity.MessageSender
import cn.luorenmu.common.annotation.BotCommand
import cn.luorenmu.request.RequestManager
import io.ktor.client.*
import io.ktor.client.call.body
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import love.forte.simbot.common.id.IntID.Companion.ID
import love.forte.simbot.common.id.LongID
import love.forte.simbot.common.id.StringID.Companion.ID
import love.forte.simbot.common.id.literal
import love.forte.simbot.common.id.toInt
import love.forte.simbot.component.onebot.v11.core.api.GetStrangerInfoApi
import love.forte.simbot.component.onebot.v11.core.api.nonstandard.OneBotNonStandardApi
import love.forte.simbot.component.onebot.v11.core.api.nonstandard.SendGroupForwardMsgApi
import love.forte.simbot.component.onebot.v11.core.api.request
import love.forte.simbot.component.onebot.v11.message.segment.OneBotForwardNode
import love.forte.simbot.component.onebot.v11.message.segment.OneBotText
import love.forte.simbot.message.Message

/**
 *
 * @author LoMu
 * Date 2025/12/30 22:34
 */
const val URL ="http://127.0.0.1:3000"
@BotCommand(id = "fakeMessage", alias = "消息伪造", value = "<qq> <message>", adapter = [Adapter.ONE_BOT])
class FakeMessageCommand : CommandEvent {
    @OptIn(OneBotNonStandardApi::class)
    override suspend fun listen(
        sender: MessageSender,
        command: Map<String, String>,
    ): Message? {
        val qq = command["qq"] ?: return null
        val message = command["message"] ?: return null

        val userInfoApi = GetStrangerInfoApi.create(qq.ID)
        val response = userInfoApi.request(RequestManager.api,Url(URL))
        val deserialize = userInfoApi.deserialize(response.bodyAsText())
        val nickname = deserialize.data?.nickname ?: ""

        val create: List<OneBotForwardNode> =
            listOf(OneBotForwardNode.create(qq.ID, nickname, listOf(OneBotText.create(message))))
        val group = sender.groupOpenId.toInt {
            if (literal.contains("-")) {
                literal.split("-")[0].toInt()
            } else literal.toInt()
        }
        val api = SendGroupForwardMsgApi.create(group.ID, create)
        api.request(RequestManager.api, Url(URL))
        return null
    }

}
