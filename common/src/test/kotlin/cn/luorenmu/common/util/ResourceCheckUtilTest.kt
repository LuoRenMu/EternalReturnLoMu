package cn.luorenmu.common.util

import java.nio.file.Files
import java.io.ByteArrayOutputStream
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 *
 * @author LoMu
 * Date 2026/8/16 15:30
 */
class ResourceCheckUtilTest {
    @AfterTest
    fun clearCache() = ResourceCheckUtil.clearCache()

    @Test
    fun normalizesPathsAndOnlyCachesNonEmptyFiles() {
        val directory = Files.createTempDirectory("resource-check-test")
        val nestedDirectory = Files.createDirectories(directory.resolve("nested"))
        val file = nestedDirectory.resolve("..").resolve("image.png")
        Files.createFile(file)
        assertFalse(ResourceCheckUtil.checkResource(file))

        Files.write(file, png())
        ResourceCheckUtil.markResourceValid(file)
        assertTrue(ResourceCheckUtil.checkResource(directory.resolve("image.png")))

        directory.toFile().deleteRecursively()
    }

    @Test
    fun startupChecksOnceAndPersistsOnlyCompleteImages() {
        val directory = Files.createTempDirectory("image-integrity-startup-test")
        try {
            val images = Files.createDirectories(directory.resolve("resources/images"))
            val good = images.resolve("good.png")
            val broken = images.resolve("broken.png")
            Files.write(good, png())
            Files.write(broken, byteArrayOf(1, 2, 3))
            val manifest = directory.resolve("data/image-integrity.properties")
            val first = ImageIntegrityStore(directory.resolve("resources"), manifest)

            assertEquals(ImageIntegrityStore.ScanResult(2, 0, 1), first.scanExistingImages())
            assertTrue(Files.isRegularFile(manifest))
            assertEquals(1, first.recordCount())
            assertFalse(first.check(broken))

            var validations = 0
            val second = ImageIntegrityStore(directory.resolve("resources"), manifest) { _, _ ->
                validations++
                false
            }
            assertEquals(ImageIntegrityStore.ScanResult(1, 1, 1), second.scanExistingImages())
            assertTrue(second.check(good))
            assertEquals(1, validations)
            Files.write(good, byteArrayOf(9, 8, 7))
            assertFalse(ImageIntegrityStore(directory.resolve("resources"), manifest).check(good))
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun incompleteDownloadCannotReplaceExistingImage() {
        val directory = Files.createTempDirectory("image-integrity-download-test")
        try {
            val target = directory.resolve("resources/images/item/1.png")
            val store = ImageIntegrityStore(directory.resolve("resources"), directory.resolve("data/image-integrity.properties"))
            val original = png()
            store.installVerified(target, original)
            assertTrue(store.check(target))

            assertFailsWith<IllegalArgumentException> { store.installVerified(target, original.copyOf(original.size - 8)) }
            assertContentEquals(original, Files.readAllBytes(target))
            assertTrue(store.check(target))

            var validations = 0
            val failsAfterWrite = ImageIntegrityStore(directory.resolve("resources"), directory.resolve("data/image-integrity.properties")) { _, _ ->
                ++validations == 1
            }
            assertFailsWith<IllegalArgumentException> { failsAfterWrite.installVerified(target, png(width = 2)) }
            assertEquals(2, validations)
            assertContentEquals(original, Files.readAllBytes(target))
            Files.list(target.parent).use { files -> assertFalse(files.anyMatch { it.fileName.toString().endsWith(".part") }) }

            val replacement = png(width = 2)
            store.installVerified(target, replacement)
            assertContentEquals(replacement, Files.readAllBytes(target))
            assertTrue(ImageIntegrityStore(directory.resolve("resources"), directory.resolve("data/image-integrity.properties")).check(target))
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun svgMustBeWellFormedAndHaveSvgRoot() {
        val directory = Files.createTempDirectory("image-integrity-svg-test")
        try {
            val store = ImageIntegrityStore(directory.resolve("resources"), directory.resolve("data/image-integrity.properties"))
            val target = directory.resolve("resources/images/icon.svg")
            store.installVerified(target, "<svg xmlns=\"http://www.w3.org/2000/svg\"><rect width=\"1\" height=\"1\"/></svg>".toByteArray())
            assertTrue(store.check(target))
            assertFailsWith<IllegalArgumentException> { store.installVerified(target, "<svg><rect></svg>".toByteArray()) }
            assertFailsWith<IllegalArgumentException> { store.installVerified(target, "<html/>".toByteArray()) }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun jpegRequiresCompleteEndMarker() {
        val directory = Files.createTempDirectory("image-integrity-jpeg-test")
        try {
            val store = ImageIntegrityStore(directory.resolve("resources"), directory.resolve("data/image-integrity.properties"))
            val target = directory.resolve("resources/images/bg/banner.jpg")
            val bytes = ByteArrayOutputStream().use { output ->
                ImageIO.write(BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "jpg", output)
                output.toByteArray()
            }
            store.installVerified(target, bytes)
            assertTrue(store.check(target))
            assertFailsWith<IllegalArgumentException> { store.installVerified(target, bytes.copyOf(bytes.size - 2)) }
            assertContentEquals(bytes, Files.readAllBytes(target))
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    private fun png(width: Int = 1): ByteArray = ByteArrayOutputStream().use { output ->
        ImageIO.write(BufferedImage(width, 1, BufferedImage.TYPE_INT_ARGB), "png", output)
        output.toByteArray()
    }
}
