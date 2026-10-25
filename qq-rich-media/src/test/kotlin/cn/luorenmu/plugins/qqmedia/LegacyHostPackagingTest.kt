package cn.luorenmu.plugins.qqmedia

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.io.TempDir
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Path
import java.util.jar.JarFile
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.*

class LegacyHostPackagingTest {
    @TempDir lateinit var root: Path

    @Test fun `plugin packages adapter and sqlite formatter without changing host dialect discovery`() {
        JarFile(System.getProperty("qq.media.jar")).use { jar ->
            assertNotNull(jar.getJarEntry("cn/luorenmu/common/database/KtormConnectionKt.class"))
            assertNotNull(jar.getJarEntry("org/ktorm/support/sqlite/SQLiteDialect.class"))
            assertNull(jar.getJarEntry("org/ktorm/database/Database.class"), "Shared Ktorm interfaces must remain parent-loaded")
            assertNull(jar.getJarEntry("META-INF/services/org.ktorm.database.SqlDialect"), "Do not add a default dialect provider to old hosts")
        }
    }

    @Test fun `deployed legacy host loads new database adapter and executes permission DSL without core replacement`() {
        val host = System.getenv("LOMU_COMPAT_HOST_JAR")
        assumeTrue(!host.isNullOrBlank(), "Requires a deployed pre-adapter host JAR fixture")
        URLClassLoader(arrayOf(Path.of(host!!).toUri().toURL()), ClassLoader.getPlatformClassLoader()).use { parent ->
            assertFailsWith<ClassNotFoundException> { parent.loadClass("cn.luorenmu.common.database.KtormConnectionKt") }
            PluginLoader(Path.of(System.getProperty("qq.media.jar")).toUri().toURL(), parent).use { plugin ->
                assertSame(parent, plugin.loadClass("org.ktorm.database.Database").classLoader)
                assertSame(plugin, plugin.loadClass("cn.luorenmu.common.database.KtormConnectionKt").classLoader)
                assertSame(plugin, plugin.loadClass("org.ktorm.support.sqlite.SQLiteDialect").classLoader)
                val type = plugin.loadClass("cn.luorenmu.plugins.qqmedia.QQGroupPushPermissions")
                val permissions = type.getConstructor(Path::class.java).newInstance(root.resolve("permissions.db"))
                val update = type.getMethod("updatePermission", String::class.java, Boolean::class.javaPrimitiveType, Long::class.javaPrimitiveType)
                val allowed = type.getMethod("canPush", String::class.java)
                assertEquals(1, update.invoke(permissions, "fixture", true, 100L))
                assertEquals(true, allowed.invoke(permissions, "fixture"))
                update.invoke(permissions, "fixture", false, 100L)
                assertEquals(0, update.invoke(permissions, "fixture", true, 100L))
                assertEquals(false, allowed.invoke(permissions, "fixture"))
            }
        }
    }

    @Test fun `separately loaded live and player plugins share the old hosts query gate`() {
        val host = System.getenv("LOMU_COMPAT_HOST_JAR")
        assumeTrue(!host.isNullOrBlank(), "Requires deployed host fixture")
        URLClassLoader(arrayOf(Path.of(host!!).toUri().toURL()), ClassLoader.getPlatformClassLoader()).use { parent ->
            val url = Path.of(System.getProperty("qq.media.jar")).toUri().toURL()
            PluginLoader(url, parent).use { live -> PluginLoader(url, parent).use { player ->
                val function = parent.loadClass("kotlin.jvm.functions.Function1")
                val continuation = parent.loadClass("kotlin.coroutines.Continuation")
                val context = parent.loadClass("kotlin.coroutines.EmptyCoroutineContext").getField("INSTANCE").get(null)
                val completed = CountDownLatch(2)
                val starts = CopyOnWriteArrayList<Long>()
                val failures = CopyOnWriteArrayList<String>()
                val executor = Executors.newFixedThreadPool(2)
                try {
                    val jobs = listOf(live, player).map { loader -> executor.submit {
                        val block = Proxy.newProxyInstance(parent, arrayOf(function)) { _, method, _ ->
                            check(method.name == "invoke")
                            starts += System.nanoTime()
                            Thread.sleep(30)
                            "fixture"
                        }
                        val resume = Proxy.newProxyInstance(parent, arrayOf(continuation)) { _, method, arguments ->
                            when (method.name) {
                                "getContext" -> context
                                "resumeWith" -> {
                                    val result = arguments!![0]
                                    if (result?.javaClass?.name == "kotlin.Result\$Failure") failures += result.toString()
                                    completed.countDown()
                                    null
                                }
                                else -> error("Unexpected continuation operation")
                            }
                        }
                        val type = loader.loadClass("cn.luorenmu.plugins.qqmedia.BackgroundPolls")
                        assertSame(parent, loader.loadClass("cn.luorenmu.common.util.StringLockUtil").classLoader)
                        type.getMethod("query", function, continuation).invoke(type.getField("INSTANCE").get(null), block, resume)
                    } }
                    jobs.forEach { it.get(5, TimeUnit.SECONDS) }
                    assertTrue(completed.await(7, TimeUnit.SECONDS))
                    assertTrue(failures.isEmpty(), failures.toString())
                    assertEquals(2, starts.size)
                    assertTrue(starts.sorted().let { it[1] - it[0] } >= 900_000_000L, "Cross-loader requests must not overlap")
                } finally { executor.shutdownNow() }
            } }
        }
    }
}

private class PluginLoader(url: URL, parent: ClassLoader) : URLClassLoader(arrayOf(url), parent) {
    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        if (!name.startsWith("cn.luorenmu.plugins.")) return super.loadClass(name, resolve)
        synchronized(getClassLoadingLock(name)) {
            findLoadedClass(name)?.let { return it }
            runCatching { findClass(name) }.getOrNull()?.let {
                if (resolve) resolveClass(it)
                return it
            }
            return super.loadClass(name, resolve)
        }
    }
}
