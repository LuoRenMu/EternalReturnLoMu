package cn.luorenmu.command

import cn.luorenmu.Adapter
import cn.luorenmu.command.entity.CommandFindResult
import cn.luorenmu.command.entity.CommandInfo
import cn.luorenmu.command.entity.MessageSender
import cn.luorenmu.command.plugin.CommandPlugins
import cn.luorenmu.currentAdapter
import cn.luorenmu.exception.MessageReplyException
import cn.luorenmu.repository.StatisticsRepository
import cn.luorenmu.repository.ExceptionRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.plugins.*
import io.ktor.utils.io.*
import love.forte.simbot.message.Message
import love.forte.simbot.message.toText
import org.koin.java.KoinJavaComponent.inject


/**
 *
 * @author LoMu
 * Date 2025/10/24 13:42
 */
private val log = KotlinLogging.logger {}

class CommandRouter {
    private val statisticsService: StatisticsRepository by inject(StatisticsRepository::class.java)
    private val exceptionRepository: ExceptionRepository by inject(ExceptionRepository::class.java)

    companion object {
        /**
         * KEY IS ALIAS
         * VALUE IS CommandInfo
         */
        val COMMANDS: Map<String, CommandInfo>
            get() = CommandPlugins.commands()

    }

    suspend fun call(messageSender: MessageSender): Message? {
        try {
            val found = commandFind(messageSender.plainText)
            if (found == null) {
                val disabled = CommandPlugins.disabledCommand(messageSender.plainText)
                if (disabled != null) {
                    statisticsService.recordCommandUsage(
                        "[disabled] ${disabled.commandName}",
                        messageSender.plainText,
                        messageSender.groupOpenId.toString(),
                        messageSender.senderOpenId.toString(),
                    )
                    return disabled.reply.toText()
                }
                if (currentAdapter == Adapter.QG_BOT) {
                    log.debug { "unmatched | group=${messageSender.groupOpenId} sender=${messageSender.senderOpenId} msg=${messageSender.message}" }
                    statisticsService.recordCommandUsage(
                        "[unmatched]",
                        messageSender.message,
                        messageSender.groupOpenId.toString(),
                        messageSender.senderOpenId.toString()
                    )
                }
                return null
            }
            log.info { "command -> ${found.eventObj} | group=${messageSender.groupOpenId} sender=${messageSender.senderOpenId} msg=${messageSender.plainText}" }
            statisticsService.recordCommandUsage(
                found.command.name,
                messageSender.plainText,
                messageSender.groupOpenId.toString(),
                messageSender.senderOpenId.toString()
            )
            return found.eventObj.listen(messageSender, found.commandParse)
        } catch (e: MessageReplyException) {
            return e.returnMsg.toText()
        } catch (e: HttpRequestTimeoutException) {
            log.error { e.printStack() }
            exceptionRepository.record(e, "command.timeout", messageSender.exceptionContext())
            return "已尽力向目标发送请求，但仍然无法到达 这绝对不是'LoMu'的问题。".toText()
        } catch (e: Exception) {
            log.error { e.printStack() }
            exceptionRepository.record(e, "command.unexpected", messageSender.exceptionContext())
            return "非常抱歉,内部处理发生了非预期错误,该问题可能需要内部修复\n 如后续仍存在请您加入654087758群聊反馈\uD83E\uDD7A！非常感谢orz".toText()
        }

    }

    fun commandFind(plainText: String): CommandFindResult? {
        val found = CommandTextParser.find(plainText, CommandPlugins.commands()) { command ->
            command.command.value.isNotBlank()
        } ?: return null
        val command = found.value
        return CommandFindResult(
            command.commandEvent,
            command.command,
            CommandTextParser.parseArguments(command.command.value, found.arguments),
        )
    }
}

private fun MessageSender.exceptionContext(): String = buildString {
    append("group=").append(groupOpenId)
    append("; sender=").append(senderOpenId)
    append("; command=").append(plainText.take(1_000))
}
