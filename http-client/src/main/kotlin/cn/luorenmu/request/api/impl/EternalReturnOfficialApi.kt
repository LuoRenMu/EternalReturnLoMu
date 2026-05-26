package cn.luorenmu.request.api.impl

import cn.luorenmu.request.api.PakeApi
import cn.luorenmu.request.api.entity.module.CacheTime
import cn.luorenmu.request.api.entity.response.official.EternalReturnNews
import io.ktor.client.call.body
import io.ktor.http.*

/**
 *
 * @author LoMu
 * Date 2026/5/23 14:36
 */
sealed class EternalReturnOfficialApi<T>(
    override var url: String,
    override var method: HttpMethod = HttpMethod.Get,
    override val headers: MutableMap<String, String> = mutableMapOf(),
    override val body: MutableMap<String, String> = mutableMapOf(),
    override val cacheTime: CacheTime = CacheTime.NULL,
) : PakeApi(url, method, headers, body, cacheTime) {
    override var baseUrl: String = "https://playeternalreturn.com/api/v1/"

    init {
        headers["Accept-language"] = "zh-CN,zh;q=0.9,en;q=0.8,en-GB;q=0.7,en-US;q=0.6"
    }
    abstract suspend fun execute(): T
    sealed class Posts<T>(
        url: String,
        cacheTime: CacheTime = CacheTime.NULL,
    ) : EternalReturnOfficialApi<T>(url, cacheTime = cacheTime) {
        object GetNews: Posts<EternalReturnNews>("posts/news?page=1&search_type=title&search_text=") {
           override suspend fun execute():EternalReturnNews =
                call().body()
        }
    }

}

