package cn.luorenmu.plugins

import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class CharacterDetailRioPreviewTest {
    @Test
    fun renderCompactRioGuide() = runBlocking {
        val output = Path.of(System.getProperty("user.dir"), "build", "previews", "character-detail-rio-skia.png")
        Files.createDirectories(output.parent)
        cn.luorenmu.nutdraw.NutDraw.render(cn.luorenmu.plugins.character.CharacterDetailTemplate(), RioCharacterDetailSample.create(), output)
        assertTrue(Files.size(output) > 100_000, "莉央角色详情预览应包含完整本地图片资源")
        println("莉央角色详情预览保留在: $output")
    }
}
