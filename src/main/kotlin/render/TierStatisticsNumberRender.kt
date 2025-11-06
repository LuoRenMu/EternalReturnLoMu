package cn.luorenmu.render

import cn.luorenmu.common.util.BrowserPool
import cn.luorenmu.common.util.PathUtils
import cn.luorenmu.request.entity.module.DakGGServerName
import love.forte.simbot.message.OfflineImage
import java.nio.file.Path

/**
 *
 * @author LoMu
 * Date 2025/11/1 23:16
 */
class TierStatisticsNumberRender : TemplateRender {
    val outputPath: Path = PathUtils.resourcesPathResolve("render", "tier", "tierStatisticsNumber.png")
    override val template: String
        get() = "/template/tierStatisticsNumber"

    fun render(t: DakGGServerName): OfflineImage {
        val serverUrl = defaultHTTPServer + template + "?serverName=${t.value}"
        BrowserPool.getBrowser().screenshotSelector(serverUrl, outputPath, "#tier_box")
        return OfflineImage.fileOfflineImage(outputPath.toString())
    }
}