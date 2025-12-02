package cn.luorenmu.exception

import love.forte.simbot.message.Message

open class MessageReplyException(val returnMsg: Message, error: String = returnMsg.toString()) :
    RuntimeException(error)