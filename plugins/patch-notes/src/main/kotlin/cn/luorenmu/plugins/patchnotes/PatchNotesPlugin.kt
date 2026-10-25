package cn.luorenmu.plugins.patchnotes

import cn.luorenmu.command.plugin.CommandPlugin
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*

/** Entire patch-note lifecycle belongs to this separately installable plugin, not the host. */
class PatchNotesPlugin(
    private val serviceFactory: () -> PatchNotesService = { officialPatchNotesService() },
    private val startupDelayMillis: Long = 5_000,
) : CommandPlugin {
    override val id = "patch-notes"
    override val name = "最新更新"
    override val version = "1.0.2"
    private val service by lazy { serviceFactory() }
    override val commands = listOf(PatchNotesCommand { service })
    private val log = KotlinLogging.logger {}
    private var job: Job? = null

    override fun onEnable() {
        if (job?.isActive == true) return
        job = CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            // The host loads plugins before installing its shared AI client in Koin.
            delay(startupDelayMillis)
            while (isActive) {
                val interval = try {
                    val count = service.scan()
                    log.info { "最新更新扫描完成：新增/恢复 $count 篇" }
                    1_800_000L
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log.warn { "最新更新扫描失败：${e.message}，一分钟后重试" }
                    60_000L
                }
                delay(interval)
            }
        }
    }

    override fun onDisable() {
        job?.cancel()
        job = null
    }
}
