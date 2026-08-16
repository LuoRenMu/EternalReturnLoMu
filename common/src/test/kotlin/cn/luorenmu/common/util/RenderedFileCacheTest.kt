package cn.luorenmu.common.util

import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 *
 * @author LoMu
 * Date 2026/8/16
 */
class RenderedFileCacheTest {
    @Test
    fun reusesRenderedFileForTwelveHoursAndRefreshesExpiredFile() = runBlocking {
        val directory = Files.createTempDirectory("rendered-file-cache-test")
        val output = directory.resolve("result.png")
        val generations = AtomicInteger()
        try {
            suspend fun render() = RenderedFileCache.getOrCreate(output) { path ->
                Files.writeString(path, "generation-${generations.incrementAndGet()}")
            }

            render()
            render()
            assertEquals(1, generations.get())
            assertEquals("generation-1", Files.readString(output))

            Files.setLastModifiedTime(output, FileTime.from(Instant.now().minus(13, ChronoUnit.HOURS)))
            render()
            assertEquals(2, generations.get())
            assertEquals("generation-2", Files.readString(output))
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun cacheKeyIsStableAndIncludesEveryParameter() {
        assertEquals(
            RenderedFileCache.cacheKey("莉央", "SQUAD", "RANK", "diamond_plus"),
            RenderedFileCache.cacheKey("莉央", "SQUAD", "RANK", "diamond_plus"),
        )
        assertNotEquals(
            RenderedFileCache.cacheKey("莉央", "SQUAD", "RANK", "diamond_plus"),
            RenderedFileCache.cacheKey("莉央", "SQUAD", "COBALT", "diamond_plus"),
        )
    }

    @Test
    fun deletesOnlyExpiredPngFilesWithTheRequestedPrefix() = runBlocking {
        val directory = Files.createTempDirectory("rendered-file-cleanup-test")
        val expiredImage = Files.writeString(directory.resolve("character_detail_old.png"), "old")
        val freshImage = Files.writeString(directory.resolve("character_detail_fresh.png"), "fresh")
        val unrelatedImage = Files.writeString(directory.resolve("character_stats_old.png"), "unrelated")
        val nonPngFile = Files.writeString(directory.resolve("character_detail_old.txt"), "text")
        val oldTime = FileTime.from(Instant.now().minus(13, ChronoUnit.HOURS))
        try {
            listOf(expiredImage, unrelatedImage, nonPngFile).forEach { Files.setLastModifiedTime(it, oldTime) }

            RenderedFileCache.getOrCreate(
                path = directory.resolve("character_detail_current.png"),
                cleanupPrefix = "character_detail_",
            ) { path -> Files.writeString(path, "current") }

            assertFalse(Files.exists(expiredImage))
            assertTrue(Files.exists(freshImage))
            assertTrue(Files.exists(unrelatedImage))
            assertTrue(Files.exists(nonPngFile))
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
