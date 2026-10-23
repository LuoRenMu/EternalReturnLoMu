package cn.luorenmu.plugins.qqmedia

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import love.forte.simbot.component.qguild.bot.QGBot
import love.forte.simbot.qguild.QQGuildApiException
import love.forte.simbot.qguild.api.GetQQGuildApi
import love.forte.simbot.qguild.api.SimpleGetApiDescription

/** Nullable fields deliberately distinguish an omitted permission from an explicit false. */
@Serializable
data class QQGroupBotState(
    @SerialName("member_openid") val memberOpenid: String? = null,
    @SerialName("joined_at") val joinedAt: String? = null,
    @SerialName("allow_proactive_msg") val allowProactiveMsg: Boolean? = null,
    @SerialName("recv_msg_setting") val recvMsgSetting: String? = null,
    /** This is the BOT's role, never the sender's role. */
    @SerialName("member_role") val memberRole: String? = null,
)

/** Backport of upstream dev's GetGroupBotStateApi, implemented using the installed SDK 4.5 API base. */
class QQGroupBotStateApi private constructor(groupOpenid: String) : GetQQGuildApi<QQGroupBotState>() {
    companion object : SimpleGetApiDescription("/v2/groups/{group_openid}/bot_state") {
        @JvmStatic fun create(groupOpenid: String): QQGroupBotStateApi {
            require(groupOpenid.matches(Regex("[A-Fa-f0-9]{32}"))) { "Invalid group OpenID" }
            return QQGroupBotStateApi(groupOpenid)
        }
    }
    override val path: Array<String> = arrayOf("v2", "groups", groupOpenid, "bot_state")
    override val resultDeserializationStrategy: DeserializationStrategy<QQGroupBotState>
        get() = QQGroupBotState.serializer()
}

enum class QQGroupPermissionStatus { ENABLED, DISABLED, NO_API_PERMISSION, NOT_PROVIDED, FAILED, TIMEOUT }
data class QQGroupPermissionResult(val status: QQGroupPermissionStatus, val errorCode: Int? = null)

object QQGroupPermissionQuery {
    suspend fun query(bot: QGBot, groupOpenid: String): QQGroupPermissionResult {
        val api = QQGroupBotStateApi.create(groupOpenid)
        return try {
            val state = bot.executeData(api)
            QQGroupPermissionResult(when (state.allowProactiveMsg) {
                true -> QQGroupPermissionStatus.ENABLED
                false -> QQGroupPermissionStatus.DISABLED
                null -> QQGroupPermissionStatus.NOT_PROVIDED
            })
        } catch (error: CancellationException) {
            throw error
        } catch (error: QQGuildApiException) {
            // SDK 4.5's three-argument exception constructor may omit info, but retains ErrInfo in its message.
            val code = error.info?.code ?: Regex("response info: ErrInfo\\(code=([0-9]+)")
                .find(error.message.orEmpty())?.groupValues?.get(1)?.toIntOrNull() ?: error.value
            QQGroupPermissionResult(if (code == 11253) QQGroupPermissionStatus.NO_API_PERMISSION
                else QQGroupPermissionStatus.FAILED, code)
        } catch (_: Exception) {
            QQGroupPermissionResult(QQGroupPermissionStatus.FAILED)
        }
    }
}
