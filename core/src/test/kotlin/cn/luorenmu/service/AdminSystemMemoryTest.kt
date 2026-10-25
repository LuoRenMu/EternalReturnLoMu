package cn.luorenmu.service

import java.lang.management.ManagementFactory
import kotlin.test.*

class AdminSystemMemoryTest {
    @Test fun `physical memory is read via exported interface on modular JDK`() {
        val bean = ManagementFactory.getOperatingSystemMXBean()
        if (bean is com.sun.management.OperatingSystemMXBean) {
            assertTrue(assertNotNull(bean.readLong("getTotalMemorySize", "getTotalPhysicalMemorySize")) > 0)
            assertTrue(assertNotNull(bean.readLong("getFreeMemorySize", "getFreePhysicalMemorySize")) >= 0)
        }
        assertNull(Any().readLong("getTotalMemorySize"))
    }
}
