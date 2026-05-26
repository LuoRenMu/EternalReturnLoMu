package cn.luorenmu.request.api.entity.response.dakgg

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

/**
 * DakGG 同步响应
 * 
 * 可能的响应格式：
 * 1. 成功: {"user_num":1099390,"player_name":"내리다는살인이다"}
 * 2. 未找到: {"not_found":true}
 * 3. 限流: {"retry_after":1000}
 *
 * @author LoMu
 * Date 2026/4/6 12:53
 */
@Serializable
data class DakGGSyncResponse(
    /**
     * 用户编号（成功时返回）
     */
    @SerialName("user_num")
    val userNum: Int? = null,
    
    /**
     * 玩家名称（成功时返回）
     */
    @SerialName("player_name")
    val playerName: String? = null,
    
    /**
     * 是否未找到（未找到时返回）
     */
    @SerialName("not_found")
    val notFound: Boolean? = null,
    
    /**
     * 重试等待时间（毫秒，限流时返回）
     */
    @SerialName("retry_after")
    val retryAfter: Int? = null
) {
    /**
     * 判断是否成功响应
     */
    fun isSuccess(): Boolean = userNum != null && playerName != null
    
    /**
     * 判断是否未找到
     */
    fun isNotFound(): Boolean = notFound == true
    
    /**
     * 判断是否被限流
     */
    fun isRateLimited(): Boolean = retryAfter != null
}
