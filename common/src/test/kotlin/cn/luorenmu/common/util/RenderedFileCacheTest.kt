package cn.luorenmu.common.util

import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 *
 * @author LoMu
 * Date 2026/8/16
 */
class RenderedFileCacheTest {
    @Test
    fun reusesRenderedFileForTwelveHoursAndOverwritesExpiredFileAtTheSamePath() = runBlocking {
        val directory = Files.createTempDirectory("rendered-file-cache-test")
        val output = directory.resolve("result.png")
        val sibling = Files.writeString(directory.resolve("another-player.png"), "keep")
        val generations = AtomicInteger()
        try {
            suspend fun render() = RenderedFileCache.getOrCreate(output) { path ->
                Files.writeString(path, "generation-${generations.incrementAndGet()}")
            }

            val firstPath = render()
            val cachedPath = render()
            assertEquals(firstPath, cachedPath)
            assertEquals(1, generations.get())
            assertEquals("generation-1", Files.readString(output))

            Files.setLastModifiedTime(output, FileTime.from(Instant.now().minus(13, ChronoUnit.HOURS)))
            val refreshedPath = render()
            assertEquals(firstPath, refreshedPath)
            assertEquals(2, generations.get())
            assertEquals("generation-2", Files.readString(output))
            assertTrue(Files.exists(sibling))
            assertEquals("keep", Files.readString(sibling))
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
}
