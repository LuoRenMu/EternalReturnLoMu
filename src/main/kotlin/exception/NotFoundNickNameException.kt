package cn.luorenmu.exception

import love.forte.simbot.message.Message


class NotFoundNickNameException(returnMsg: Message) :
    MessageReplyException(returnMsg) {
}