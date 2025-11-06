package cn.luorenmu.request.api

import cn.luorenmu.request.RequestManager
import io.ktor.client.statement.*
import io.ktor.http.*

/**
 *
 * @author LoMu
 * Date 2025/11/1 02:29
 */

/**
 *  PakeApi 通常处理文本类数据，如：json、xml、text
 */
abstract class PakeApi(
    override var url: String,
    override var method: HttpMethod = HttpMethod.Companion.Get,
    override val headers: MutableMap<String, String> = mutableMapOf(),
    override val body: MutableMap<String, String> = mutableMapOf(),
    override val cacheTime: Long = 0L,
) : Api {


    override fun toString(): String {
        return "Api(baseUrl='$baseUrl', url='$url', method=$method, headers=$headers, body=$body)"
    }

    suspend fun call(): HttpResponse {
        return RequestManager.call(this)
    }

}