package cn.luorenmu.command

import cn.luorenmu.command.entity.CommandFindResult
import cn.luorenmu.command.entity.CommandInfo
import cn.luorenmu.command.entity.MessageSender
import cn.luorenmu.common.annotation.BotCommand
import cn.luorenmu.common.util.ReflectionUtil
import cn.luorenmu.currentAdapter
import io.github.oshai.kotlinlogging.KotlinLogging
import love.forte.simbot.message.Message


/**
 *
 * @author LoMu
 * Date 2025/10/24 13:42
 */
private val log = KotlinLogging.logger {}

class CommandRouter {
    companion object {
        /**
         * KEY IS ALIAS
         * VALUE IS CommandInfo
         */
        private val COMMANDS by lazy {
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
        val result = commandFind(messageSender.plainText)?.let {
            it.eventObj.listen(messageSender, it.commandParse)
        }
        return result
    }

    fun commandFind(plainText: String): CommandFindResult? {
        if (plainText.isEmpty()) return null
        if (plainText.startsWith("/")) {
            val originCommand = plainText.replaceFirst("/", "")
            val inputCommand = originCommand.split("\\s".toRegex())
            val inputCommandFirst = inputCommand[0]
            val command = COMMANDS[inputCommandFirst] ?: run { return null }
            return CommandFindResult(
                command.commandEvent,
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