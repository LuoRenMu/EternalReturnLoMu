import io.ktor.client.HttpClient
import io.ktor.client.request.request
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readText
import io.ktor.http.cio.CIOHeaders

/**
 *
 * @author LoMu
 * Date 2025/10/11 10:23
 */
class Test

suspend fun main() {

}

/**
 * @param template 命令样式  sample -> search <nickname> <mode> <season>
 * @param input    输入内容  sample -> 查询玩家 螺母 钴协议 赛季6
 * @return 当为null时 不符合条件 否则正常返回   sample -> {nickname=螺母, mode=钴协议, season=赛季6}
 *
 */
fun parseCommand(template: String, input: String): Map<String, String>? {
    val templateSpilt = template.split("\\s+".toRegex())
    val templateFirst = templateSpilt.first().trim()
    val inputSplit = input.split("\\s+".toRegex())
    val inputFirst = inputSplit.first().trim()
    if (inputFirst != templateFirst || inputFirst.isBlank() || inputFirst.isBlank()) {
        return null
    }

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
