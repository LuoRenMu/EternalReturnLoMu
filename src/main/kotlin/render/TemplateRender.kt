package cn.luorenmu.render

import cn.luorenmu.SERVER_PORT
import love.forte.simbot.message.OfflineImage
import java.nio.file.Path

/**
 *
 * @author LoMu
 * Date 2025/11/1 23:17
 */
interface TemplateRender {

    val defaultHTTPServer: String
        get() = "http://localhost:$SERVER_PORT"
    val template: String
}