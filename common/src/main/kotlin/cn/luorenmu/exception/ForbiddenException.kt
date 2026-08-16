package cn.luorenmu.exception

/**
 *
 * @author LoMu
 * Date 2026/8/16 15:30
 */
class ForbiddenException(e: String = "已拒绝本次请求.游戏可能正在更新或机器人已被禁用") :
    MessageReplyException(e)
