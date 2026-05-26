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
    init {
        plainText = plainText.trim()
    }
}
