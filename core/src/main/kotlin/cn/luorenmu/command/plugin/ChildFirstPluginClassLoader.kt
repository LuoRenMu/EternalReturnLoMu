package cn.luorenmu.command.plugin

import java.net.URL
import java.net.URLClassLoader

/**
 * Keeps shared interfaces parent-loaded while allowing a plugin Module to replace its own classes.
 *
 * @author LoMu
 * Date 2026/8/16 15:30
 */
internal class ChildFirstPluginClassLoader(
    urls: Array<URL>,
    parent: ClassLoader,
) : URLClassLoader(urls, parent) {
    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        if (!name.startsWith(PLUGIN_PACKAGE_PREFIX)) return super.loadClass(name, resolve)
        synchronized(getClassLoadingLock(name)) {
            findLoadedClass(name)?.let { return it }
            runCatching { findClass(name) }.getOrNull()?.let {
                if (resolve) resolveClass(it)
                return it
            }
            return super.loadClass(name, resolve)
        }
    }

    private companion object {
        const val PLUGIN_PACKAGE_PREFIX = "cn.luorenmu.plugins."
    }
}
