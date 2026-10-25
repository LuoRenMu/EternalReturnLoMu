package cn.luorenmu.plugins.bililive

import java.util.jar.JarFile
import java.net.URL
import java.net.URLClassLoader
import java.lang.reflect.Proxy
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.sql.Driver
import java.util.Properties
import kotlin.test.*

class BiliLivePluginPackagingTest {
    @TempDir lateinit var root: Path
    @Test fun `old deployed core loads both split plugins and enforces trusted sender roles without a core upgrade`() {
        val hostJar = System.getenv("LOMU_COMPAT_HOST_JAR")
        assumeTrue(!hostJar.isNullOrBlank(), "Requires deployed host fixture")
        URLClassLoader(arrayOf(Path.of(hostJar!!).toUri().toURL()), ClassLoader.getPlatformClassLoader()).use { host ->
            val loaderType = host.loadClass("cn.luorenmu.command.plugin.ChildFirstPluginClassLoader")
            val ctor = loaderType.getDeclaredConstructor(Array<URL>::class.java, ClassLoader::class.java).apply { isAccessible = true }
            val live = ctor.newInstance(arrayOf(Path.of(System.getProperty("live.plugin.jar")).toUri().toURL()), host) as URLClassLoader
            val test = ctor.newInstance(arrayOf(Path.of(System.getProperty("test.plugin.jar")).toUri().toURL()), host) as URLClassLoader
            live.use { test.use {
                val testPlugin = test.loadClass("cn.luorenmu.plugins.test.TestPlugin").getConstructor().newInstance()
                assertEquals("1.9.0", testPlugin.javaClass.getMethod("getVersion").invoke(testPlugin))
                assertEquals(1, (testPlugin.javaClass.getMethod("getCommands").invoke(testPlugin) as List<*>).size)
                val fn0 = host.loadClass("kotlin.jvm.functions.Function0")
                val fn2 = host.loadClass("kotlin.jvm.functions.Function2")
                val monitor = Proxy.newProxyInstance(host, arrayOf(fn0)) { _, _, _ -> error("menu must not access monitor") }
                val permission = Proxy.newProxyInstance(host, arrayOf(fn2)) { _, method, _ -> check(method.name == "invoke"); true }
                val commandType = live.loadClass("cn.luorenmu.plugins.bililive.BiliLiveCommand")
                val command = commandType.getConstructor(fn0, fn2).newInstance(monitor, permission)
                val idType = host.loadClass("love.forte.simbot.common.id.ID")
                val stringId = host.loadClass("love.forte.simbot.common.id.StringID").getMethod("valueOf", String::class.java)
                val senderType = host.loadClass("cn.luorenmu.command.entity.MessageSender")
                val sender = senderType.getConstructor(idType, String::class.java, idType, String::class.java, String::class.java)
                    .newInstance(stringId.invoke(null, "A".repeat(32)), "fixture", stringId.invoke(null, "B".repeat(32)), "/主播订阅", "/主播订阅")
                val continuation = host.loadClass("kotlin.coroutines.Continuation")
                val context = host.loadClass("kotlin.coroutines.EmptyCoroutineContext").getField("INSTANCE").get(null)
                fun invoke(): Any {
                    val completion = CompletableFuture<Any>()
                    val resume = Proxy.newProxyInstance(host, arrayOf(continuation)) { _, method, arguments ->
                        when (method.name) {
                            "getContext" -> context
                            "resumeWith" -> { completion.complete(arguments!![0]); null }
                            else -> error("Unexpected continuation call")
                        }
                    }
                    val result = commandType.getMethod("listen", senderType, Map::class.java, continuation).invoke(command, sender, emptyMap<String, String>(), resume)
                    val suspended = host.loadClass("kotlin.coroutines.intrinsics.IntrinsicsKt").getMethod("getCOROUTINE_SUSPENDED")
                        .apply { isAccessible = true }.invoke(null)
                    return if (result === suspended) completion.get(5, TimeUnit.SECONDS) else result
                }
                assertContains(invoke().toString(), LIVE_ACCESS_NOTICE)
                senderType.getMethod("setQqGroupMemberRole", String::class.java).invoke(sender, "admin")
                assertContains(invoke().toString(), LIVE_ACCESS_NOTICE)
                senderType.getMethod("setQqGroupMemberRole", String::class.java).invoke(sender, "owner")
                assertTrue(host.loadClass("love.forte.simbot.message.Messages").isInstance(invoke()))

                // Exercise the actual embedded DSL/dialects with the deployed core's Ktorm and JDBC binaries.
                val fn1 = host.loadClass("kotlin.jvm.functions.Function1")
                val unit = host.loadClass("kotlin.Unit").getField("INSTANCE").get(null)
                val driver = host.loadClass("org.sqlite.JDBC").getConstructor().newInstance() as Driver
                val connection = Proxy.newProxyInstance(host, arrayOf(fn1)) { _, method, args ->
                    check(method.name == "invoke")
                    driver.connect("jdbc:sqlite:${root.resolve("old-host.db")}", Properties()).use { c ->
                        fn1.getMethod("invoke", Any::class.java).invoke(args!![0], c)
                    }
                    unit
                }
                val repoType = live.loadClass("cn.luorenmu.plugins.bililive.BiliLiveRepository")
                fun repository() = repoType.getConstructor(fn1).newInstance(connection)
                val repo = repository()
                val claimant = "B".repeat(32)
                assertEquals(true, repoType.getMethod("claim", String::class.java, String::class.java).invoke(repo, LIVE_CLAIM_GROUP, claimant))
                assertEquals(true, repoType.getMethod("hasClaim", String::class.java).invoke(repository(), claimant))
                val roomType = live.loadClass("cn.luorenmu.plugins.bililive.BiliRoom")
                fun room(id: Long, status: Int = 0, session: String = "") = roomType.getConstructor(Long::class.javaPrimitiveType, String::class.java, Int::class.javaPrimitiveType,
                    String::class.java, String::class.java, String::class.java, Long::class.javaPrimitiveType)
                    .newInstance(id, "fixture", status, session, "up", "", 0L)
                val add = repoType.getMethod("add", String::class.java, String::class.java, roomType)
                assertEquals("ADDED", add.invoke(repo, "group1", claimant, room(1)).toString())
                assertEquals("ADDED", add.invoke(repo, "group2", claimant, room(1)).toString())
                assertEquals("USER_LIMIT", add.invoke(repo, "group3", claimant, room(2)).toString())
                assertEquals("GROUP_LIMIT", add.invoke(repo, "group1", claimant, room(2)).toString())
                assertEquals(2, (repoType.getMethod("activeRows").invoke(repository()) as List<*>).size)
                val recordPush = repoType.getMethod("recordPush", String::class.java, Long::class.javaPrimitiveType, Long::class.javaPrimitiveType)
                assertEquals(true, recordPush.invoke(repo, "group1", 1L, 100L))
                assertEquals(true, recordPush.invoke(repo, "group2", 1L, 100L))
                val count = repoType.getMethod("pushCount", Long::class.javaObjectType, String::class.java)
                assertEquals(2L, count.invoke(repository(), 1L, null), "statistics must work with deployed core's actual JDBC and Ktorm")
                assertEquals(1L, count.invoke(repository(), 1L, "group1"))
                val rowType = live.loadClass("cn.luorenmu.plugins.bililive.BiliSubscription")
                val advance = repoType.getMethod("advance", rowType, roomType)
                fun row() = (repoType.getMethod("rows").invoke(repo) as List<*>).single {
                    rowType.getMethod("getGroup").invoke(it) == "group1"
                }
                assertEquals(true, advance.invoke(repo, row(), room(1, 1, "2026-10-23 06:35:00")))
                assertEquals(true, advance.invoke(repo, row(), room(1, 0)))
                assertEquals(true, advance.invoke(repo, row(), room(1, 1, "2026-10-23 06:35:00")))
                assertEquals(false, advance.invoke(repo, row(), room(1, 2)), "old host must preserve end deduplication after a status flap")
            } }
        }
    }

    @Test fun `standalone plugin owns polling models and legacy storage while test has no live worker`() {
        val plugin = BiliLivePlugin()
        assertEquals("bili-live", plugin.id)
        assertEquals("1.2.1", plugin.version)
        assertEquals(1, plugin.commands.size)
        JarFile(System.getProperty("live.plugin.jar")).use { jar ->
            assertEquals("cn.luorenmu.plugins.bililive.BiliLivePlugin", jar.manifest.mainAttributes.getValue("Main-Class"))
            assertNotNull(jar.getJarEntry("cn/luorenmu/plugins/bililive/BiliLiveMonitor.class"))
            assertNotNull(jar.getJarEntry("cn/luorenmu/common/database/KtormConnectionKt.class"))
            assertNotNull(jar.getJarEntry("org/ktorm/support/sqlite/SQLiteDialect.class"))
            assertNotNull(jar.getJarEntry("sql/bili-live.sql"))
            assertNotNull(jar.getJarEntry("sql/qq-push-statistics.sql"))
            assertNotNull(jar.getJarEntry("cn/luorenmu/plugins/qqmedia/QQPushStatistics.class"))
            assertTrue(jar.getInputStream(jar.getJarEntry("sql/bili-live.sql")).bufferedReader().use { it.readText() }.contains("test_bili_live"))
        }
        JarFile(System.getProperty("test.plugin.jar")).use { jar ->
            assertTrue(jar.entries().asSequence().none { it.name.contains("BiliLive") || it.name == "sql/bili-live.sql" })
            assertNotNull(jar.getJarEntry("cn/luorenmu/plugins/test/ProactiveTestCommand.class"))
        }
    }
}
