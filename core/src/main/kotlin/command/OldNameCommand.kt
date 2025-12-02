package cn.luorenmu.command

import cn.luorenmu.command.entity.MessageSender
import cn.luorenmu.common.annotation.CommandFilter
import cn.luorenmu.render.OldNameRender
import cn.luorenmu.service.EternalReturnRenderService
import love.forte.simbot.message.Message
import love.forte.simbot.message.toText
import org.koin.java.KoinJavaComponent.inject

/**
 *
 * @author LoMu
 * Date 2025/11/29 15:34
 */
@CommandFilter(id = "oldName", alias = ["曾用名"], value = "<nickname>")
class OldNameCommand : CommandEvent {
    private val eternalReturnRenderService: EternalReturnRenderService by inject(
        EternalReturnRenderService::class.java
    )

    private val render: OldNameRender by inject(OldNameRender::class.java)

    override suspend fun listen(
        sender: MessageSender,
        command: Map<String, String>,
    ): Message? {
        val nickname = command["nickname"] ?: run {
            return "请输入名称".toText()
        }
        preheatRequest(nickname)
        return render.render(nickname)
    }

    suspend fun preheatRequest(nickname: String) {
        eternalReturnRenderService.oldName(nickname)
    }

}