import cn.luorenmu.common.util.BrowserPool
import kotlin.io.path.Path


/**
 *
 * @author LoMu
 * Date 2025/11/20 00:03
 */
suspend fun main() {
    BrowserPool.getBrowser().screenshotSelector(
        "E:\\code\\Kotlin Code\\LoMu-QQBot\\core\\build\\classes\\kotlin\\main\\resources\\render\\tmp\\c25b6ea6-8a08-4ff2-9720-423b86c7f737.html",
        Path("E:\\code\\Kotlin Code\\LoMu-QQBot\\core\\build\\classes\\kotlin\\main\\resources\\render\\player\\123.png"),
        "#content-container"
    )
}

