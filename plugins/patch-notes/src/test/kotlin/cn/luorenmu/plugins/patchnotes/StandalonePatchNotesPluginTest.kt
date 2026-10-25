package cn.luorenmu.plugins.patchnotes

import cn.luorenmu.Adapter
import cn.luorenmu.command.plugin.CommandPlugins
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarFile
import kotlin.test.*

class StandalonePatchNotesPluginTest {
    @Test fun `external jar owns summarizer template command and scheduler without host discovery`() {
        val jar = Path.of(System.getProperty("patch.plugin.jar"))
        JarFile(jar.toFile()).use { archive ->
            assertEquals("cn.luorenmu.plugins.patchnotes.PatchNotesPlugin", archive.manifest.mainAttributes.getValue("Main-Class"))
            for (type in listOf("PatchNotesPlugin", "PatchNotesCommand", "PatchNotesSummarizer", "PatchImageSelector", "PatchNotesTemplate")) {
                assertNotNull(archive.getJarEntry("cn/luorenmu/plugins/patchnotes/$type.class"))
            }
            assertTrue(archive.entries().asSequence().none { it.name.startsWith("cn/luorenmu/ai/") || it.name.startsWith("cn/luorenmu/command/") })
        }
        CommandPlugins.configureBuiltins(emptyList())
        CommandPlugins.initialize(Adapter.QG_BOT, Files.createTempDirectory("standalone-patch-plugin"))
        try {
            val view = Files.newInputStream(jar).use { CommandPlugins.installJar("patch-notes.jar", it) }
            assertEquals("patch-notes", view.id)
            assertEquals("1.0.2", view.version)
            assertTrue(view.external && view.enabled)
            assertEquals(listOf("最新更新"), view.commands)
            val info = assertNotNull(CommandPlugins.commands()["最新更新"])
            assertNotEquals(PatchNotesPlugin::class.java.classLoader, info.commandEvent.javaClass.classLoader)
            val aliases = setOf("最新更新", "更新公告", "更新日志")
            assertEquals(aliases, CommandPlugins.commands().filterValues { it.commandEvent === info.commandEvent }.keys)
            aliases.forEach { assertSame(info.commandEvent, CommandPlugins.commands()[it]?.commandEvent) }
            CommandPlugins.disable("patch-notes")
            aliases.forEach { assertNull(CommandPlugins.commands()[it]) }
        } finally {
            CommandPlugins.configureBuiltins(emptyList())
            CommandPlugins.initialize(Adapter.QG_BOT, Files.createTempDirectory("standalone-patch-cleanup"))
        }
    }
}
