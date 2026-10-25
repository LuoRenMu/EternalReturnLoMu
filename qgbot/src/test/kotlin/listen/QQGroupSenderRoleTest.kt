package cn.luorenmu.qqbot.listen

import love.forte.simbot.component.qguild.group.QGGroupRole
import kotlin.test.*

class QQGroupSenderRoleTest {
    @Test fun `owner and admin are confirmed but SDK default member stays unknown`() {
        assertEquals("owner", confirmedQQGroupRole(QGGroupRole.OWNER))
        assertEquals("admin", confirmedQQGroupRole(QGGroupRole.ADMIN))
        assertNull(confirmedQQGroupRole(QGGroupRole.MEMBER))
    }
}
