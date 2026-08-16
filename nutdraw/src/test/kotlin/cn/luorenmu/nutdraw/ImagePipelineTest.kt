package cn.luorenmu.nutdraw

import cn.luorenmu.nutdraw.render.ImageByteLoader
import cn.luorenmu.nutdraw.render.ImageFormatDetector
import cn.luorenmu.nutdraw.render.ImageSource
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 *
 * @author LoMu
 * Date 2026/8/16 15:30
 */
class ImagePipelineTest {
    @Test
    fun `byte loader falls through source adapters`() {
        val expected = byteArrayOf(1, 2, 3)
        val loader = ImageByteLoader(listOf(ImageSource { null }, ImageSource { expected }))
        assertContentEquals(expected, loader.load("asset"))
    }

    @Test
    fun `format detector recognises extension and svg content`() {
        val detector = ImageFormatDetector()
        assertTrue(detector.isSvg("icon.svg?v=1", byteArrayOf()))
        assertTrue(detector.isSvg("icon", "<svg xmlns='http://www.w3.org/2000/svg'/>".encodeToByteArray()))
        assertFalse(detector.isSvg("icon.png", byteArrayOf(1, 2, 3)))
    }
}
