package cn.luorenmu.exception

import love.forte.simbot.message.toText

/**
 *
 * @author LoMu
 * Date 2025/11/27 12:39
 */
class ForbiddenException : MessageReplyException("已拒绝本次请求.游戏可能正在更新或机器人已被禁用".toText()) {
}