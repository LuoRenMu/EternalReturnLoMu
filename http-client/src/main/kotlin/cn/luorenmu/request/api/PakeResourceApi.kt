package cn.luorenmu.request.api

import cn.luorenmu.request.RequestManager
import cn.luorenmu.request.api.entity.module.CacheTime
import io.ktor.http.*
import java.nio.file.Path

/**
 *
 * @author LoMu
 * Date 2025/11/2 01:13
 */
abstract class PakeResourceApi(
    override var url: String,
    override val path: Path,
    override var method: HttpMethod = HttpMethod.Get,
    override val headers: MutableMap<String, String> = mutableMapOf(),
    override val body: MutableMap<String, String> = mutableMapOf(),
) : ResourceApi, Api {
    override val cacheTime: CacheTime = CacheTime.NULL

    companion object {
        // 我不知道URL或我根本不需要提供URL 此举的目的是获取PATH
        const val UNKNOW_URL = "Ciallo～(∠・ω< )⌒★"
    }

    override fun toString(): String {
        return "ResourceApi(baseUrl='$baseUrl', url='$url', method=$method, headers=$headers, body=$body, path=$path)"
    }


    override suspend fun callStream() {
        RequestManager.callStream(this, path)
    }

}