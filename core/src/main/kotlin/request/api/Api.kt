package cn.luorenmu.request.api

import cn.luorenmu.request.api.entity.module.CacheTime
import io.ktor.http.*

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
}