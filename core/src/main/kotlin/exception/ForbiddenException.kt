package cn.luorenmu.exception

import love.forte.simbot.message.toText

/**
 *
 * @author LoMu
 * Date 2025/11/27 12:39
 */
class ForbiddenException : MessageReplyException("服务器禁止了本次访问".toText()) {
}