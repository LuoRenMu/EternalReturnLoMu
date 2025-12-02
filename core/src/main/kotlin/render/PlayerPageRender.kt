package cn.luorenmu.render

import cn.luorenmu.common.util.BrowserPool
import cn.luorenmu.common.util.PathUtils
import love.forte.simbot.message.OfflineImage

/**
 *
 * @author LoMu
 * Date 2025/10/26 14:43
 */
class PlayerPageRender : TemplateRender() {

    val template: String
        get() = "/template/searchPlayer/"

    fun render(nickname: String): OfflineImage {
        val outputPath = PathUtils.resourcesPathResolve("render", "player", "$nickname.png")
        val serverUrl = "$defaultHTTPServer$template$nickname"
        BrowserPool.getBrowser()
            .screenshotSelector(serverUrl, outputPath, "#content-container")
        return OfflineImage.fileOfflineImage(outputPath.toString())
    }
}