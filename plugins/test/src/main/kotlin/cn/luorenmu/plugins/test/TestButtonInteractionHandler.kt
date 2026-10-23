package cn.luorenmu.plugins.test

import cn.luorenmu.command.CommandRouter
import cn.luorenmu.command.entity.MessageSender
import love.forte.simbot.common.id.StringID.Companion.ID
import love.forte.simbot.message.Message
import love.forte.simbot.message.toText
import love.forte.simbot.qguild.event.InteractionCreate

internal object TestCallbackProtocol {
    const val BUTTON_ID = "test-callback"
    const val DATA = "lomu:test:v1"
    const val COMMAND = "/test"
}

internal data class TestButtonInteraction(
    val interactionId: String,
    val replyEventId: String,
    val type: Int,
    val chatType: Int,
    val groupId: String?,
    val userId: String?,
    val buttonId: String?,
    val buttonData: String?,
) {
    companion object {
        fun from(source: InteractionCreate): TestButtonInteraction = with(source.data) {
            TestButtonInteraction(id, source.id, type, chatType, groupOpenid, groupMemberOpenid,
                data.resolved.buttonId, data.resolved.buttonData)
        }
    }
}

/** Acknowledge the bare d.id immediately, then run the allowlisted command and reply using the outer id. */
internal class TestButtonInteractionHandler(
    private val query: suspend (MessageSender) -> Message? = { CommandRouter().call(it) },
    private val recent: TestInteractionGuard = TestInteractionGuard(),
) {
    suspend fun handle(
        interaction: TestButtonInteraction,
        acknowledge: suspend (Int) -> Unit,
        sendReply: suspend (String, String, Message) -> Unit,
    ): Boolean {
        if (interaction.type != 11 || (interaction.buttonId != TestCallbackProtocol.BUTTON_ID &&
                interaction.buttonData != TestCallbackProtocol.DATA)) return false
        if (interaction.interactionId.isBlank()) return false
        if (!recent.shouldProcess(interaction.interactionId)) return true // ACK and query at most once per interaction.
        if (interaction.chatType != 1 || interaction.replyEventId.isBlank() ||
            interaction.groupId?.matches(Regex("[A-Fa-f0-9]{32}")) != true ||
            interaction.userId?.matches(Regex("[A-Fa-f0-9]{32}")) != true ||
            interaction.buttonData != TestCallbackProtocol.DATA ||
            interaction.buttonId != null && interaction.buttonId != TestCallbackProtocol.BUTTON_ID) {
            acknowledge(1)
            return true
        }
        acknowledge(0) // Do not wait for DAK.GG, rendering, or image uploading before acknowledging the click.
        val sender = MessageSender(interaction.groupId.ID, "QQButton", interaction.userId.ID,
            TestCallbackProtocol.COMMAND, TestCallbackProtocol.COMMAND)
        val reply = query(sender) ?: "测试命令未启用，请稍后重试".toText()
        sendReply(interaction.groupId, interaction.replyEventId, reply)
        return true
    }
}
