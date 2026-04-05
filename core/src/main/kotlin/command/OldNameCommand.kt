package cn.luorenmu.command

import cn.luorenmu.Adapter
import cn.luorenmu.command.entity.MessageSender
import cn.luorenmu.common.annotation.BotCommand
import cn.luorenmu.common.util.BrowserPool
import cn.luorenmu.common.util.PathUtils
import cn.luorenmu.render.FreemarkerRenderer
import cn.luorenmu.service.EternalReturnRenderService
import love.forte.simbot.message.Message
import love.forte.simbot.message.OfflineImage
import love.forte.simbot.message.toText
import org.koin.java.KoinJavaComponent.inject

/**
 *
 * @author LoMu
 * Date 2025/11/29 15:34
 */
@BotCommand(name = "曾用名", alias = "曾用名", value = "<nickname>", adapter = [Adapter.ONE_BOT], description = "查询曾用名,消耗大量请求资源.需等待较长时间")
class OldNameCommand : CommandEvent {
    private val eternalReturnRenderService: EternalReturnRenderService by inject(
        EternalReturnRenderService::class.java
    )


    override suspend fun listen(
        sender: MessageSender,
        command: Map<String, String>,
    ): Message {
        val nickname = command["nickname"] ?: run {
            return "请输入名称".toText()
        }

        val freeMarkerContent = FreemarkerRenderer.render(
            "old_name.ftl",
            eternalReturnRenderService.oldName(nickname)
        )
        val resourcesPathResolve = PathUtils.resourcesPathResolve("render", "old_name", "${nickname}.png")
        BrowserPool.getBrowser().screenshotContentSelector(
            freeMarkerContent,
            resourcesPathResolve,
            "#app"
        )

        return OfflineImage.fileOfflineImage(resourcesPathResolve.toString())
    }
}