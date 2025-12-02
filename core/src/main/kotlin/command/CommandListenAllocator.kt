package cn.luorenmu.command

import cn.luorenmu.command.entity.CommandFindResult
import cn.luorenmu.command.entity.CommandInfo
import cn.luorenmu.command.entity.MessageSender
import cn.luorenmu.common.annotation.CommandFilter
import cn.luorenmu.common.util.ReflectionUtil
import io.github.oshai.kotlinlogging.KotlinLogging
import love.forte.simbot.message.Message


/**
 *
 * @author LoMu
 * Date 2025/10/24 13:42
 */
private val log = KotlinLogging.logger {}

class CommandListenAllocator {
    companion object {
        /**
         * KEY IS CommandFilter Annotation Value Field
         * VALUE IS CommandEvent obj
         */
        private val COMMANDS by lazy {
            val c = ReflectionUtil.getSubTypesOf(this::class.java.packageName, CommandEvent::class.java)
            val result = mutableMapOf<Array<String>, CommandInfo>()
            for (klass in c) {
                if (klass.isAnnotationPresent(CommandFilter::class.java)) {
                    val command = klass.getAnnotation(CommandFilter::class.java)
                    val obj = klass.getDeclaredConstructor().newInstance() as CommandEvent
                    result[command.alias] = CommandInfo(command, obj)
                }
            }
            log.debug { "COMMANDS: $result" }
            result
        }


        /**
         *  KEY是经过加工处理后的 sample -> search <nickname>  ==>  search
         *  VALUE是原始不变的
         */
        private val COMMAND_MATCH by lazy {
            val result = mutableMapOf<String, Array<String>>()
            COMMANDS.keys.forEach { alias ->
                for (string in alias) {
                    result[string] = alias
                }
            }
            log.debug { "COMMAND_MATCH: $result" }
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
            val command = COMMAND_MATCH[inputCommandFirst] ?: run { return null }
            return CommandFindResult(
                COMMANDS[command]!!.commandEvent,
                parseCommand(COMMANDS[command]!!.commandFilter.value, originCommand)
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