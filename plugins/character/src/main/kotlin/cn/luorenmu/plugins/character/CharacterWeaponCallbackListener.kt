package cn.luorenmu.plugins.character

import cn.luorenmu.command.CommandRouter
import cn.luorenmu.command.entity.MessageSender
import cn.luorenmu.plugins.qqmedia.QQGroupInteractionReplySender
import cn.luorenmu.plugins.qqmedia.QQInteractionGuard
import cn.luorenmu.plugins.qqmedia.SimbotQQRuntime
import cn.luorenmu.request.entity.module.DakGGRank
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import love.forte.simbot.common.id.StringID.Companion.ID
import love.forte.simbot.component.qguild.event.QGInteractionCreateEvent
import love.forte.simbot.event.EventListenerRegistrationHandle
import love.forte.simbot.event.EventResult
import love.forte.simbot.event.listen
import love.forte.simbot.message.Message
import love.forte.simbot.message.toText

internal data class CharacterWeaponCallback(val characterId: Long, val weaponId: Long, val options: CharacterDetailOptions) {
    val buttonId get() = "character-weapon-$characterId-$weaponId"
    val command get() = options.command(characterId, weaponId)

    companion object {
        fun decode(data: String?): CharacterWeaponCallback? {
            val parts = data?.takeIf { it.length <= 120 }?.split(':') ?: return null
            if (parts.size != 7 || parts.take(3) != listOf("lomu", "character", "v1")) return null
            val character = parts[3].toLongOrNull()?.takeIf { it > 0 } ?: return null
            val weapon = parts[6].toLongOrNull()?.takeIf { it > 0 } ?: return null
            val options = when (parts[4]) {
                "RANK" -> CharacterDetailOptions(rank = DakGGRank.entries.firstOrNull { it.value == parts[5] } ?: return null)
                "COBALT" -> if (parts[5] == "none") CharacterDetailOptions(mode = "钴协议") else return null
                else -> return null
            }
            return CharacterWeaponCallback(character, weapon, options)
        }
    }
}

internal data class CharacterWeaponInteraction(
    val id: String, val eventId: String, val type: Int, val chatType: Int,
    val groupId: String?, val userId: String?, val buttonId: String?, val data: String?,
)

internal class CharacterWeaponInteractionHandler(
    private val query: suspend (MessageSender) -> Message? = { CommandRouter().call(it) },
    private val guard: QQInteractionGuard = QQInteractionGuard(),
) {
    suspend fun handle(event: CharacterWeaponInteraction, ack: suspend (Int) -> Unit,
                       send: suspend (String, String, Message) -> Unit): Boolean {
        if (event.type != 11 || event.buttonId?.startsWith("character-weapon-") != true) return false
        if (event.id.isBlank() || !guard.shouldProcess(event.id)) return true
        val selection = CharacterWeaponCallback.decode(event.data)
        if (selection == null || event.buttonId != selection.buttonId || event.chatType != 1 ||
            event.eventId.isBlank() || event.groupId?.matches(Regex("[A-Fa-f0-9]{32}")) != true ||
            event.userId?.matches(Regex("[A-Fa-f0-9]{32}")) != true) {
            ack(1)
            return true
        }
        ack(0)
        val sender = MessageSender(event.groupId.ID, "QQButton", event.userId.ID, selection.command, selection.command)
        send(event.groupId, event.eventId, query(sender) ?: "角色查询暂不可用，请重新发送 /查询角色".toText())
        return true
    }
}

internal object CharacterWeaponCallbackListener {
    private val log = KotlinLogging.logger {}

    fun register(): EventListenerRegistrationHandle? {
        val handler = CharacterWeaponInteractionHandler()
        return SimbotQQRuntime.applicationOrNull()?.eventDispatcher?.listen<QGInteractionCreateEvent> { event ->
            val source = event.sourceEventEntity
            val data = source.data
            try {
                handler.handle(CharacterWeaponInteraction(data.id, source.id, data.type, data.chatType,
                    data.groupOpenid, data.groupMemberOpenid, data.data.resolved.buttonId, data.data.resolved.buttonData),
                    ack = { event.respond(it) },
                    send = { group, eventId, reply -> QQGroupInteractionReplySender.send(event.bot, group, eventId, reply) })
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                log.warn { "角色武器回调失败: ${error.javaClass.simpleName}" }
            }
            EventResult.empty()
        }
    }
}
