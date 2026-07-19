package cn.luorenmu.command

import cn.luorenmu.Adapter
import cn.luorenmu.command.entity.CommandFindResult
import cn.luorenmu.command.entity.CommandInfo
import cn.luorenmu.command.entity.MessageSender
import cn.luorenmu.common.annotation.BotCommand
import cn.luorenmu.common.util.ReflectionUtil
import cn.luorenmu.currentAdapter
import cn.luorenmu.exception.MessageReplyException
import cn.luorenmu.repository.StatisticsRepository
import com.microsoft.playwright.TimeoutError
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

    companion object {
        /**
         * KEY IS ALIAS
         * VALUE IS CommandInfo
         */
        val COMMANDS by lazy {
            val c = ReflectionUtil.getSubTypesOf(this::class.java.packageName, CommandEvent::class.java)
            val result = mutableMapOf<String, CommandInfo>()

            for (klass in c) {
                if (klass.isAnnotationPresent(BotCommand::class.java)) {
                    val command = klass.getAnnotation(BotCommand::class.java)
                    val obj = klass.getDeclaredConstructor().newInstance() as CommandEvent
                    if (command.adapter.contains(currentAdapter)) {
                        result[command.alias] = CommandInfo(command, obj)
                    }
                }
            }
            log.debug { "current adapter -> $currentAdapter , load command ${result.keys}" }
            result
        }

    }

    suspend fun call(messageSender: MessageSender): Message? {
        try {
            val found = commandFind(messageSender.plainText)
            if (found == null) {
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
        } catch (e: TimeoutError) {
            log.error { e.printStack() }
            return "任务超时,非常抱歉!请稍后再试。".toText()
        } catch (e: HttpRequestTimeoutException) {
            log.error { e.printStack() }
            return "已尽力向目标发送请求，但仍然无法到达 这绝对不是'LoMu'的问题。".toText()
        } catch (e: Exception) {
            log.error { e.printStack() }
            return "非常抱歉,内部处理发生了非预期错误,该问题可能需要内部修复\n 如后续仍存在请您加入654087758群聊反馈\uD83E\uDD7A！非常感谢orz".toText()
        }

    }

    fun commandFind(plainText: String): CommandFindResult? {
        if (plainText.isEmpty()) return null
        if (plainText.startsWith("/")) {
            val originCommand = plainText.replaceFirst("/", "")
            val inputCommand = originCommand.split("\\s".toRegex())
            val inputCommandFirst = inputCommand[0]
            val command = COMMANDS[inputCommandFirst] ?: run { return null }
            return CommandFindResult(
                command.commandEvent, command.command,
                parseCommand(command.command.value, originCommand)
            )
        }
        return null
    }


    /**
     * @param template 命令样式  sample -> <nickname> <mode> <season>
     * @param input    输入内容  sample -> 查询玩家 螺母 钴协议 赛季6
     * @return 当为null时 不符合条件 否则正常返回   sample -> {nickname=螺母, mode=钴协议, season=赛季6}
     *
     */
    private fun parseCommand(template: String, input: String): Map<String, String> {
        val templateSpilt = template.split("\\s+".toRegex())
        val inputSplit = input.split("\\s+".toRegex()).drop(1)
        val commandMap = mutableMapOf<String, String>()
        val regexPattern = "<(.*?)>".toRegex()
        for ((index, value) in templateSpilt.withIndex()) {
            if (index > inputSplit.size - 1) break
            val result = regexPattern.find(value)
            val key = result?.groupValues[1] ?: continue
            commandMap[key] = inputSplit[index]
        }
        return commandMap
    }

}