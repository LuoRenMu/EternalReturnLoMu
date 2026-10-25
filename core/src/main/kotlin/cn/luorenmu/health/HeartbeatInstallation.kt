package cn.luorenmu.health

import cn.luorenmu.Adapter
import cn.luorenmu.ConfigFile
import cn.luorenmu.common.util.DatabaseManager
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopping
import io.ktor.util.AttributeKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible

val CoreHeartbeatKey = AttributeKey<CoreHeartbeat>("CoreHeartbeat")

internal fun Application.installHeartbeat(adapter: Adapter, database: DatabaseManager, adapterChecks: List<HealthCheck>) {
    val config = ConfigFile.config.heartbeat
    val checks = adapterChecks.ifEmpty {
        listOf(HealthCheck("adapter", "机器人适配器") { HealthResult(false, "未注册适配器探针") })
    } + HealthCheck("database", "数据库") {
        runInterruptible(Dispatchers.IO) {
            database.useConnection { connection ->
                HealthResult(connection.isValid(config.timeoutSeconds))
            }
        }
    }
    val heartbeat = CoreHeartbeat(config, adapter.name, checks, SmtpHealthNotifier(config.smtp))
    attributes.put(CoreHeartbeatKey, heartbeat)
    environment.monitor.subscribe(ApplicationStopping) { heartbeat.stop() }
    heartbeat.start(this)
}
