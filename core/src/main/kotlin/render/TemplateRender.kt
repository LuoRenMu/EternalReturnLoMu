package cn.luorenmu.render

import cn.luorenmu.SERVER_PORT

/**
 *
 * @author LoMu
 * Date 2025/11/1 23:17
 */
abstract class TemplateRender {

    val defaultHTTPServer: String
        get() = "http://localhost:$SERVER_PORT"
}