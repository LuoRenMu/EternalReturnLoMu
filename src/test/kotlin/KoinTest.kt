import cn.luorenmu.service.ResourcesDownloadService
import kotlinx.coroutines.asCoroutineDispatcher
import org.koin.core.context.startKoin
import org.koin.dsl.module
import java.util.concurrent.Executors

/**
 *
 * @author LoMu
 * Date 2025/11/2 01:32
 */
suspend fun main() {
    val executor = Executors.newFixedThreadPool(10)
    val appModule = module {
        single { executor.asCoroutineDispatcher() }
    }
    val koin = startKoin {
        modules(appModule)
    }

    try {

    } finally {
        koin.close()
        executor.shutdown()
    }
}