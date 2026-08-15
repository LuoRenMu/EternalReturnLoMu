package cn.luorenmu.plugins.tier

import java.util.jar.JarFile
import kotlin.test.Test
import kotlin.test.assertNotNull

class TierPluginPackagingTest {
    @Test
    fun `standalone plugin jar contains its TierStatistics model`() {
        val jarPath = checkNotNull(System.getProperty("tier.plugin.jar"))

        JarFile(jarPath).use { jar ->
            assertNotNull(
                jar.getJarEntry("cn/luorenmu/plugins/tier/TierStatistics.class"),
                "tier plugin must package its runtime model instead of relying on the host core version",
            )
        }
    }
}
