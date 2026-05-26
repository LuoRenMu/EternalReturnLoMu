package cn.luorenmu.exception

open class MessageReplyException(val returnMsg: String, error: String = returnMsg) :
    RuntimeException(error)