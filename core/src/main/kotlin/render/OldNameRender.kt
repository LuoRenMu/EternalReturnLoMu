package cn.luorenmu.render

import cn.luorenmu.common.util.BrowserPool
import cn.luorenmu.common.util.PathUtils
import love.forte.simbot.message.OfflineImage

/**
 *
 * @author LoMu
 * Date 2025/11/29 16:10
 */
class OldNameRender: TemplateRender() {
    val template: String
        get() = "/template/oldName/"

    fun render(nickname: String): OfflineImage {
        val outputPath = PathUtils.resourcesPathResolve("render", "oldName", "$nickname.png")
        val serverUrl = "$defaultHTTPServer$template$nickname"
        BrowserPool.getBrowser()
            .screenshotSelector(serverUrl, outputPath, "#app")
        return OfflineImage.fileOfflineImage(outputPath.toString())
    }
}