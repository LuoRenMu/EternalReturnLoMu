package cn.luorenmu.request.api

import cn.luorenmu.request.api.entity.module.CacheTime
import io.ktor.http.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async

/**
 *
 * @author LoMu
 * Date 2025/10/25 17:27
 */
sealed interface Api {
    var baseUrl: String
    var url: String
    var method: HttpMethod
    val headers: MutableMap<String, String>
    val body: MutableMap<String, String>
    val cacheTime: CacheTime

    companion object{
        fun <T> CoroutineScope.ioAsync(block: suspend CoroutineScope.() -> T) =
            async(Dispatchers.IO, block = block)
    }
}