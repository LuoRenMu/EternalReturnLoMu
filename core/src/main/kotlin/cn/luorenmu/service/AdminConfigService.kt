package cn.luorenmu.service

import cn.luorenmu.ConfigFile
import cn.luorenmu.SERVER_PORT
import cn.luorenmu.common.util.DatabaseManager
import kotlinx.serialization.Serializable

/**
 *
 * @author LoMu
 * Date 2026/8/16 15:30
 */
class AdminConfigService(private val databaseManager: DatabaseManager) {
    fun view(): AdminConfigView = ConfigFile.config.toView()

    private fun ConfigFile.BotConfig.toView() = AdminConfigView(
        port = port,
        runtimePort = SERVER_PORT,
        adminToken = adminToken.masked(),
        apiKey = apiKey.masked(),
        databaseBackend = databaseManager.displayName(),
        other = other.mapValues { (key, value) ->
            if (isSensitiveKey(key) && value.isNotBlank()) SECRET_MASK else value
        },
        postgres = PostgresConfigView(
            postgres.host,
            postgres.port,
            postgres.database,
            postgres.user,
            postgres.password.masked(),
            postgres.schema,
        ),
        ai = AiConfigView(ai.apiKey.masked(), ai.model, ai.baseUrl),
    )

    private fun String.masked() = if (isBlank()) "" else SECRET_MASK

    private fun isSensitiveKey(key: String): Boolean {
        val normalized = key.lowercase().replace("-", "_")
        return SENSITIVE_KEY_PARTS.any(normalized::contains)
    }

    companion object {
        const val SECRET_MASK = "********"
        private val SENSITIVE_KEY_PARTS = listOf("secret", "token", "password", "api_key", "apikey")
    }
}

@Serializable
data class AdminConfigView(
    val port: Int,
    val runtimePort: Int,
    val adminToken: String,
    val apiKey: String,
    val databaseBackend: String,
    val other: Map<String, String>,
    val postgres: PostgresConfigView,
    val ai: AiConfigView,
)

@Serializable
data class PostgresConfigView(
    val host: String,
    val port: Int,
    val database: String,
    val user: String,
    val password: String,
    val schema: String,
)

@Serializable
data class AiConfigView(val apiKey: String, val model: String, val baseUrl: String)
