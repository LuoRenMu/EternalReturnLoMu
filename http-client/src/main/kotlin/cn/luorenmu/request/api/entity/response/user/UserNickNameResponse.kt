package cn.luorenmu.request.api.entity.response.user

import kotlinx.serialization.Serializable

/**
 *
 * @author LoMu
 * Date 2025/10/25 16:00
 */
@Serializable
data class UserNickNameResponse(
    val code: Int,
    val message: String,
    val description: String?,
    val user: User,
) {
    @Serializable
    data class User(val userId: String, val nickname: String)
}
