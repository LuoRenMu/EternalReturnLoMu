package cn.luorenmu.command

import cn.luorenmu.command.entity.CommandFindResult
import cn.luorenmu.command.entity.MessageSender
import cn.luorenmu.common.annotation.CommandFilter
import cn.luorenmu.common.util.ReflectionUtil
import love.forte.simbot.component.qguild.event.QGGroupAtMessageCreateEvent
import love.forte.simbot.event.Event
import org.slf4j.LoggerFactory


/**
 *
 * @author LoMu
 * Date 2025/10/24 13:42
 */
private val log = LoggerFactory.getLogger("cn.luorenmu.LoMu-QQBot")

class CommandListenAllocator {
    companion object {
        /**
         * KEY IS CommandFilter Annotation Value Field
         * VALUE IS CommandEvent obj
         */
        private val COMMANDS by lazy {
            val c = ReflectionUtil.getSubTypesOf(this::class.java.packageName, CommandEvent::class.java)
            val result = mutableMapOf<String, CommandEvent>()
            for (klass in c) {
                if (klass.isAnnotationPresent(CommandFilter::class.java)) {
                    val command = klass.getAnnotation(CommandFilter::class.java).value
                    val obj = klass.getDeclaredConstructor().newInstance() as CommandEvent
                    result[command] = obj
                }
            }
            result
        }


        /**
         *  KEY是经过加工处理后的 sample -> search <nickname>  ==>  search
         *  VALUE是原始不变的
         */
        private val COMMAND_MATCH by lazy {
            val result = mutableMapOf<String, String>()
            COMMANDS.keys.forEach { key ->
                val split = key.split("\\s".toRegex())
                result[split[0].trim()] = key
            }
            result
        }
    }

    suspend fun call(event: Event) {
        when (event) {
            is QGGroupAtMessageCreateEvent -> {
                val plainText = event.messageContent.plainText.trim()
                val result = commandFind(plainText)
                result?.let { it ->
                    it.eventObj.listen(MessageSender.builder(event, it.commandParse))?.apply {
                        event.reply(this)
                    }
                } ?: run {
                    // 未找到命令
                }
            }
        }
    }

    fun commandFind(plainText: String): CommandFindResult? {
        if (plainText.isEmpty()) return null
        if (plainText.startsWith("/")) {
            val originCommand = plainText.replaceFirst("/", "")
            val inputCommand = originCommand.split("\\s".toRegex())
            val inputCommandFirst = inputCommand[0]
            log.debug("COMMAND_MATCH: {}", COMMAND_MATCH)
            log.debug("COMMANDS: {}", COMMANDS)
            val command = COMMAND_MATCH[inputCommandFirst] ?: run { return null }
            return CommandFindResult(COMMANDS[command]!!, parseCommand(command, originCommand))
        }
        return null
    }


    /**
     * @param template 命令样式  sample -> search <nickname> <mode> <season>
     * @param input    输入内容  sample -> 查询玩家 螺母 钴协议 赛季6
     * @return 当为null时 不符合条件 否则正常返回   sample -> {nickname=螺母, mode=钴协议, season=赛季6}
     *
     */
    private fun parseCommand(template: String, input: String): Map<String, String> {
        val templateSpilt = template.split("\\s+".toRegex())
        val inputSplit = input.split("\\s+".toRegex())

        val commandMap = mutableMapOf<String, String>()
        for ((index, s) in templateSpilt.withIndex()) {
            if (index == 0) continue
            if (index > inputSplit.size - 1) break
            val regexPattern = "<(.*?)>".toRegex().find(s)
            val key = regexPattern?.groupValues[1] ?: continue
            commandMap[key] = inputSplit[index]
        }
        return commandMap
    }

}