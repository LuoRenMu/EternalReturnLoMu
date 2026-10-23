package cn.luorenmu.command

import cn.luorenmu.command.entity.MessageSender
import love.forte.simbot.common.id.ID
import love.forte.simbot.common.id.StringID.Companion.ID
import kotlin.test.*

class MessageSenderCompatibilityTest {
    @Test fun `QQ role metadata preserves existing five argument constructor ABI and defaults unknown`() {
        assertNotNull(MessageSender::class.java.getConstructor(ID::class.java, String::class.java,
            ID::class.java, String::class.java, String::class.java))
        val sender = MessageSender("group".ID, "name", "user".ID, "/test", " /test ")
        assertNull(sender.qqGroupMemberRole)
        assertEquals("/test", sender.plainText)
        sender.qqGroupMemberRole = "owner"
        assertEquals("owner", sender.qqGroupMemberRole)
    }
}
