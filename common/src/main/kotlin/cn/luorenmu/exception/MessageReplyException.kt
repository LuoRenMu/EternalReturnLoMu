package cn.luorenmu.exception

/**
 *
 * @author LoMu
 * Date 2026/8/16 15:30
 */
open class MessageReplyException(val returnMsg: String, error: String = returnMsg) :
    RuntimeException(error)
