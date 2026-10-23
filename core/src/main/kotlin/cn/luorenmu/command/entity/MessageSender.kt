package cn.luorenmu.command.entity

import love.forte.simbot.common.id.ID

/**
 *
 * @author LoMu
 * Date 2025/10/24 13:46
 */
data class MessageSender(
    var groupOpenId: ID,
    var senderName: String,
    var senderOpenId: ID,
    var message: String,
    var plainText:String,
){
    /** Verified positive identity from a QQ event; null means unavailable, NOT a normal member.
     * Kept outside the primary constructor to preserve existing plugin constructor/copy ABI.
     */
    var qqGroupMemberRole: String? = null

    init {
        plainText = plainText.trim()
    }
}
