package cn.luorenmu.plugins.patchnotes

import cn.luorenmu.Adapter
import cn.luorenmu.command.CommandEvent
import cn.luorenmu.command.entity.CommandOptional
import cn.luorenmu.command.entity.MessageSender
import cn.luorenmu.common.annotation.BotCommand
import love.forte.simbot.message.Message
import love.forte.simbot.message.OfflineImage
import love.forte.simbot.message.toText

@BotCommand(name = "最新更新", alias = "最新更新", aliases = ["更新公告", "更新日志"], value = "",
    adapter = [Adapter.QG_BOT, Adapter.ONE_BOT])
class PatchNotesCommand(private val service: () -> PatchNotesService = { officialPatchNotesService() }) : CommandEvent {
    override val description = "只返回最新官方补丁的AI摘要与正文配图，按文章ID缓存"
    override val example = "/最新更新"
    override val optionals = emptyList<CommandOptional>()
    override suspend fun listen(sender: MessageSender, command: Map<String, String>): Message {
        val image = service().latestImage() ?: return "最新更新正在分析中；系统只扫描最新一篇公告，每30分钟检查，请稍后重试。".toText()
        return OfflineImage.fileOfflineImage(image.toString())
    }
}
