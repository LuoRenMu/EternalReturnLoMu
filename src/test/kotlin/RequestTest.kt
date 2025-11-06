import cn.luorenmu.request.api.EternalReturnDakGGApi
import io.ktor.client.call.body
import io.ktor.client.statement.bodyAsText

/**
 *
 * @author LoMu
 * Date 2025/11/5 23:20
 */
suspend fun main() {
    println(EternalReturnDakGGApi.User.GetProfile("神圣审判").call().bodyAsText())
}