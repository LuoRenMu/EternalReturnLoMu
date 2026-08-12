package cn.luorenmu.common.util

import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResourceCheckUtilTest {
    @AfterTest
    fun clearCache() = ResourceCheckUtil.clearCache()

    @Test
    fun normalizesPathsAndOnlyCachesNonEmptyFiles() {
        val directory = Files.createTempDirectory("resource-check-test")
        val file = directory.resolve("nested").resolve("..").resolve("image.png")
        Files.createDirectories(file.parent)
        Files.createFile(file)
        assertFalse(ResourceCheckUtil.checkResource(file))

        Files.write(file, byteArrayOf(1, 2, 3))
        ResourceCheckUtil.markResourceValid(file)
        assertTrue(ResourceCheckUtil.checkResource(directory.resolve("image.png")))

        directory.toFile().deleteRecursively()
    }
}
