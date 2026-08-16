package cn.luorenmu.service

import cn.luorenmu.SERVER_PORT
import cn.luorenmu.common.util.DatabaseManager
import cn.luorenmu.currentAdapter
import kotlinx.serialization.Serializable
import java.lang.management.ManagementFactory
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 *
 * @author LoMu
 * Date 2026/8/16 15:30
 */
class AdminSystemService(
    private val databaseManager: DatabaseManager,
) {
    private val runtime = Runtime.getRuntime()
    private val runtimeBean = ManagementFactory.getRuntimeMXBean()
    private val operatingSystemBean = ManagementFactory.getOperatingSystemMXBean()
    private val dateFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.systemDefault())

    fun view(): AdminSystemView {
        val now = System.currentTimeMillis()
        return AdminSystemView(
            startedAt = dateFormatter.format(Instant.ofEpochMilli(runtimeBean.startTime)),
            serverTime = dateFormatter.format(Instant.ofEpochMilli(now)),
            uptimeMillis = runtimeBean.uptime,
            osName = operatingSystemBean.name,
            osVersion = operatingSystemBean.version,
            osArch = operatingSystemBean.arch,
            processors = operatingSystemBean.availableProcessors,
            javaVersion = System.getProperty("java.version").orEmpty(),
            javaVendor = System.getProperty("java.vendor").orEmpty(),
            jvmMemoryUsedBytes = runtime.totalMemory() - runtime.freeMemory(),
            jvmMemoryMaxBytes = runtime.maxMemory(),
            systemMemoryTotalBytes = operatingSystemBean.readLong("getTotalMemorySize", "getTotalPhysicalMemorySize"),
            systemMemoryFreeBytes = operatingSystemBean.readLong("getFreeMemorySize", "getFreePhysicalMemorySize"),
            databaseBackend = databaseManager.displayName(),
            adapter = runCatching { currentAdapter.name }.getOrDefault("UNKNOWN"),
            runtimePort = SERVER_PORT,
        )
    }
}

@Serializable
data class AdminSystemView(
    val startedAt: String,
    val serverTime: String,
    val uptimeMillis: Long,
    val osName: String,
    val osVersion: String,
    val osArch: String,
    val processors: Int,
    val javaVersion: String,
    val javaVendor: String,
    val jvmMemoryUsedBytes: Long,
    val jvmMemoryMaxBytes: Long,
    val systemMemoryTotalBytes: Long?,
    val systemMemoryFreeBytes: Long?,
    val databaseBackend: String,
    val adapter: String,
    val runtimePort: Int,
)

private fun Any.readLong(vararg methodNames: String): Long? {
    return methodNames.firstNotNullOfOrNull { methodName ->
        runCatching {
            javaClass.methods
                .firstOrNull { it.name == methodName && it.parameterCount == 0 }
                ?.invoke(this) as? Number
        }.getOrNull()?.toLong()
    }
}
