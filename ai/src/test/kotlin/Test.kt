import cn.luorenmu.ai.AIConfig
import cn.luorenmu.ai.KoogLLMClient
import cn.luorenmu.ai.news.NewsClassifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URL


/**
 *
 * @author LoMu
 * Date 2026/8/8 23:11
 */
suspend fun main() {
    val content = withContext(Dispatchers.IO) {
        URL("https://playeternalreturn.com/posts/news/3752").openConnection()
    }
    val text = StringBuilder()
    withContext(Dispatchers.IO) {
        BufferedReader(
            InputStreamReader(withContext(Dispatchers.IO) {
                content.getInputStream()
            }, "UTF-8")
        ).use { reader ->

            var line: String?
            while ((reader.readLine().also { line = it }) != null) {
                text.append(line)
            }
            println(text.toString())
        }
    }

    val koogLLMClient = KoogLLMClient(AIConfig())
    val classify = NewsClassifier(koogLLMClient).classify("与妮娅展开紧张刺激的骰子对决！", text.toString())
    println(classify)
}