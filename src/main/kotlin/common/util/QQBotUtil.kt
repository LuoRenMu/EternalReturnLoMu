package common.util

import cn.luorenmu.APP_ID

/**
 * @author LoMu
 * Date 2025/10/22 23:38
 */
object QQBotUtil {

    fun getQQAvatarUrl(appId: String = APP_ID, openId: String, size: Int = 640) =
        "https://thirdqq.qlogo.cn/qqapp/${appId}/${openId}/${640}"
}
