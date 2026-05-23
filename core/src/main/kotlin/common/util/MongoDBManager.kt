package cn.luorenmu.common.util

import cn.luorenmu.ConfigFile
import com.mongodb.kotlin.client.coroutine.MongoClient
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 *
 * @author LoMu
 * Date 2026/5/1 18:33
 */
class MongoDBManager {
    private val logger = KotlinLogging.logger {}
    private val client by lazy {
        if (ConfigFile.config.mongo.enabled) {
            MongoClient.create(ConfigFile.config.mongo.connectionString)
        } else {
            null
        }
    }

    val database by lazy {
        client?.getDatabase(ConfigFile.config.mongo.database)
    }

    fun isEnabled(): Boolean = ConfigFile.config.mongo.enabled

    fun close() {
        client?.close()
    }
}