package cn.luorenmu.command.plugin

import cn.luorenmu.Adapter
import cn.luorenmu.command.CommandAliasRegistry
import cn.luorenmu.command.CommandTextParser
import cn.luorenmu.command.entity.CommandInfo
import cn.luorenmu.common.annotation.BotCommand
import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.io.InputStream
import java.util.Properties
import java.util.concurrent.atomic.AtomicReference
import java.util.jar.JarFile
import kotlin.io.path.extension
import kotlin.io.path.name

/**
 * Core plugin loader. Registry publication is atomic: in-flight commands finish on the old
 * Implementation while new calls immediately see the enabled/reloaded Module snapshot.
 */
object CommandPlugins {
    private val log = KotlinLogging.logger {}
    private val registry = AtomicReference<Map<String, CommandInfo>>(emptyMap())
    private val disabledRegistry = AtomicReference<Map<String, DisabledCommand>>(emptyMap())
    private val slots = linkedMapOf<String, PluginSlot>()
    private val builtinFactories = linkedMapOf<String, CommandPluginFactory>()
    private var adapter: Adapter? = null
    private var pluginDirectory: Path? = null
    private val savedStates = linkedMapOf<String, SavedPluginState>()

    @Synchronized
    fun configureBuiltins(factories: List<CommandPluginFactory>) {
        builtinFactories.clear()
        (listOf(CommandPluginFactory(::CoreCommandPlugin)) + factories).forEach { factory ->
            val plugin = factory.create()
            require(plugin.id.isValidPluginId()) { "Invalid plugin id: ${plugin.id}" }
            require(builtinFactories.put(plugin.id, factory) == null) { "Duplicate plugin id: ${plugin.id}" }
        }
    }

    @Synchronized
    fun initialize(adapter: Adapter, directory: Path) {
        closeSlots()
        this.adapter = adapter
        pluginDirectory = directory.toAbsolutePath().normalize()
        builtinFactories.putIfAbsent(CORE_PLUGIN_ID, CommandPluginFactory(::CoreCommandPlugin))
        Files.createDirectories(pluginDirectory)
        Files.createDirectories(cacheDirectory())
        loadStates()

        builtinFactories.forEach { (id, factory) ->
            val state = savedStates[id]
            slots[id] = PluginSlot(
                factory.create(),
                enabled = id == CORE_PLUGIN_ID || state?.enabled != false,
                disabledReply = state?.disabledReply.orEmpty(),
                source = PluginSource.Builtin(factory),
            )
        }
        externalJars().forEach { jar ->
            runCatching { loadExternal(jar) }
                .onFailure { log.error(it) { "Failed to load command plugin jar: $jar" } }
        }
        publish()
        log.info { "Loaded command plugins: ${slots.keys}" }
    }

    fun commands(): Map<String, CommandInfo> = registry.get()

    fun disabledCommand(plainText: String): DisabledCommandMatch? {
        val found = CommandTextParser.find(plainText, disabledRegistry.get()) { it.command.value.isNotBlank() }
            ?: return null
        return DisabledCommandMatch(found.value.pluginId, found.value.command.name, found.value.reply)
    }

    fun views(): List<CommandPluginView> = synchronized(this) {
        slots.values.map { it.view() }
    }

    @Synchronized
    fun enable(id: String): CommandPluginView {
        val slot = slots[id] ?: error("Plugin not found: $id")
        if (!slot.enabled) {
            validate(slot.plugin, excludingId = id)
            slot.plugin.onEnable()
            slot.enabled = true
            publish()
            saveState(slot)
        }
        return slot.view()
    }

    @Synchronized
    fun disable(id: String): CommandPluginView {
        require(id != CORE_PLUGIN_ID) { "Core plugin cannot be disabled" }
        val slot = slots[id] ?: error("Plugin not found: $id")
        if (slot.enabled) {
            slot.enabled = false
            publish()
            slot.plugin.onDisable()
            saveState(slot)
        }
        return slot.view()
    }

    @Synchronized
    fun setDisabledReply(id: String, reply: String): CommandPluginView {
        require(id != CORE_PLUGIN_ID) { "Core plugin does not use a disabled reply" }
        require(reply.length <= MAX_DISABLED_REPLY_LENGTH) { "Disabled reply is too long" }
        val slot = slots[id] ?: error("Plugin not found: $id")
        slot.disabledReply = reply.trim()
        publish()
        saveState(slot)
        return slot.view()
    }

    @Synchronized
    fun installJar(fileName: String, input: InputStream): CommandPluginView {
        val safeName = fileName.substringAfterLast('/').substringAfterLast('\\')
        require(safeName.matches(Regex("[A-Za-z0-9._-]+\\.jar", RegexOption.IGNORE_CASE))) { "Invalid plugin jar name" }
        val directory = checkNotNull(pluginDirectory) { "Plugin loader is not initialized" }
        val temporary = directory.resolve(".upload-${System.nanoTime()}-$safeName")
        try {
            Files.newOutputStream(temporary).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    require(total <= MAX_PLUGIN_BYTES) { "Plugin jar exceeds 25 MB" }
                    output.write(buffer, 0, read)
                }
            }
            val candidate = createExternalSlot(temporary, enabled = false)
            val candidateId = candidate.plugin.id
            try {
                validate(candidate.plugin, excludingId = candidate.plugin.id)
            } finally {
                closeSlot(candidate)
            }
            val target = directory.resolve(safeName)
            val existingAtTarget = slots.values.firstOrNull {
                (it.source as? PluginSource.External)?.originalJar == target.toAbsolutePath().normalize()
            }
            require(existingAtTarget == null || existingAtTarget.plugin.id == candidateId) {
                "Uploaded jar cannot change plugin id from ${existingAtTarget?.plugin?.id} to $candidateId"
            }
            val previousOriginal = (slots[candidateId]?.source as? PluginSource.External)?.originalJar
            runCatching {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            }.getOrElse {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
            }
            loadExternal(target)
            if (previousOriginal != null && previousOriginal != target.toAbsolutePath().normalize()) {
                Files.deleteIfExists(previousOriginal)
            }
            publish()
            return slots.values.first { (it.source as? PluginSource.External)?.originalJar == target.toAbsolutePath().normalize() }.view()
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    /** Reloads one Module. External jars are shadow-copied first, so the deployed jar stays replaceable on Windows. */
    @Synchronized
    fun reload(id: String): CommandPluginView {
        val old = slots[id] ?: error("Plugin not found: $id")
        val replacement = when (val source = old.source) {
            is PluginSource.Builtin -> PluginSlot(source.factory.create(), old.enabled, old.disabledReply, source)
            is PluginSource.External -> createExternalSlot(source.originalJar, old.enabled, old.disabledReply)
        }
        try {
            require(replacement.plugin.id == id) {
                "Reloaded plugin id changed from $id to ${replacement.plugin.id}"
            }
            if (replacement.enabled) {
                validate(replacement.plugin, excludingId = id)
                replacement.plugin.onEnable()
            }
        } catch (error: Throwable) {
            closeSlot(replacement)
            throw error
        }
        slots[id] = replacement
        publish()
        closeSlot(old)
        return replacement.view()
    }

    /** Discovers newly copied jars and reloads already installed external Modules. */
    @Synchronized
    fun reloadAll(): List<CommandPluginView> {
        val jars = externalJars()
        val jarByFile = jars.associateBy { it.fileName.toString() }
        slots.values.filter { it.source is PluginSource.External }.map { it.plugin.id }.forEach { id ->
            val old = slots[id] ?: return@forEach
            val source = old.source as PluginSource.External
            if (jarByFile.containsKey(source.originalJar.fileName.toString())) reload(id)
            else {
                slots.remove(id)
                closeSlot(old)
                builtinFactories[id]?.let { factory ->
                    val state = savedStates[id]
                    slots[id] = PluginSlot(factory.create(), state?.enabled != false, state?.disabledReply.orEmpty(), PluginSource.Builtin(factory))
                }
            }
        }
        val installedFiles = slots.values.mapNotNull { (it.source as? PluginSource.External)?.originalJar?.fileName?.toString() }.toSet()
        jars.filterNot { it.fileName.toString() in installedFiles }.forEach { loadExternal(it) }
        publish()
        return slots.values.map { it.view() }
    }

    private fun loadExternal(jar: Path) {
        val state = savedStates[providerPluginId(jar)]
        val slot = createExternalSlot(jar, enabled = state?.enabled != false, disabledReply = state?.disabledReply.orEmpty())
        try {
            if (slot.enabled) {
                validate(slot.plugin, excludingId = slot.plugin.id)
                slot.plugin.onEnable()
            }
            val replaced = slots.put(slot.plugin.id, slot)
            replaced?.let(::closeSlot)
        } catch (error: Throwable) {
            closeSlot(slot)
            throw error
        }
    }

    private fun createExternalSlot(jar: Path, enabled: Boolean, disabledReply: String = ""): PluginSlot {
        val original = jar.toAbsolutePath().normalize()
        require(original.parent == pluginDirectory) { "Plugin jar must be directly inside $pluginDirectory" }
        val providerName = providerClassName(original)
        require(providerName.startsWith("cn.luorenmu.plugins.")) {
            "Plugin provider must use cn.luorenmu.plugins package: $providerName"
        }
        val shadow = cacheDirectory().resolve("${original.fileName}-${System.nanoTime()}.jar")
        Files.copy(original, shadow, StandardCopyOption.REPLACE_EXISTING)
        val loader = ChildFirstPluginClassLoader(arrayOf(shadow.toUri().toURL()), CommandPlugin::class.java.classLoader)
        return try {
            val plugin = loader.loadClass(providerName).getDeclaredConstructor().newInstance() as CommandPlugin
            require(plugin.id.isValidPluginId()) { "Invalid plugin id: ${plugin.id}" }
            require(plugin.id != CORE_PLUGIN_ID) { "External plugin cannot replace core" }
            PluginSlot(plugin, enabled, disabledReply, PluginSource.External(original, shadow, loader))
        } catch (error: Throwable) {
            loader.close()
            Files.deleteIfExists(shadow)
            throw error
        }
    }

    private fun providerClassName(jar: Path): String {
        JarFile(jar.toFile()).use { archive ->
            archive.manifest?.mainAttributes?.getValue("Main-Class")?.trim()?.takeIf { it.isNotBlank() }
                ?.let { return it }
        }
        error("Missing Main-Class manifest attribute in ${jar.name}")
    }

    private fun providerPluginId(jar: Path): String {
        val candidate = createExternalSlot(jar, enabled = false)
        return try { candidate.plugin.id } finally { closeSlot(candidate) }
    }

    private fun validate(plugin: CommandPlugin, excludingId: String?) {
        val target = linkedMapOf<String, CommandInfo>()
        slots.values.filter { it.enabled && it.plugin.id != excludingId }.forEach { register(it.plugin, target) }
        register(plugin, target)
    }

    private fun publish() {
        val next = linkedMapOf<String, CommandInfo>()
        slots.values.filter { it.enabled }.forEach { register(it.plugin, next) }
        registry.set(next.toMap())
        val disabled = linkedMapOf<String, DisabledCommand>()
        slots.values.filterNot { it.enabled }.forEach { slot ->
            slot.plugin.commands.forEach { event ->
                val command = event.javaClass.getAnnotation(BotCommand::class.java) ?: return@forEach
                if (checkNotNull(adapter) in command.adapter) {
                    val value = DisabledCommand(slot.plugin.id, command, slot.disabledReply.ifBlank { "该命令当前已停用" })
                    (listOf(command.alias) + command.aliases).forEach { alias -> disabled.putIfAbsent(alias, value) }
                }
            }
        }
        disabledRegistry.set(disabled.toMap())
    }

    private fun register(plugin: CommandPlugin, target: MutableMap<String, CommandInfo>) {
        val currentAdapter = checkNotNull(adapter) { "Command plugin loader is not initialized" }
        plugin.commands.forEach { event ->
            val annotation = event.javaClass.getAnnotation(BotCommand::class.java)
                ?: error("Command ${event.javaClass.name} in ${plugin.id} is missing @BotCommand")
            if (currentAdapter in annotation.adapter) {
                CommandAliasRegistry.register(target, annotation, CommandInfo(annotation, event))
            }
        }
    }

    private fun externalJars(): List<Path> = pluginDirectory?.let { directory ->
        Files.list(directory).use { stream ->
            stream.filter { Files.isRegularFile(it) && it.extension.equals("jar", ignoreCase = true) }
                .sorted()
                .toList()
        }
    }.orEmpty()

    private fun cacheDirectory(): Path = checkNotNull(pluginDirectory).resolve(".cache")

    private fun closeSlots() {
        slots.values.forEach(::closeSlot)
        slots.clear()
        registry.set(emptyMap())
        disabledRegistry.set(emptyMap())
    }

    private fun closeSlot(slot: PluginSlot) {
        runCatching { if (slot.enabled) slot.plugin.onDisable() }
            .onFailure { log.warn(it) { "Plugin shutdown failed: ${slot.plugin.id}" } }
        val source = slot.source as? PluginSource.External ?: return
        runCatching { source.loader.close() }
        runCatching { Files.deleteIfExists(source.shadowJar) }
    }

    private fun PluginSlot.view() = CommandPluginView(
        id = plugin.id,
        name = plugin.name,
        version = plugin.version,
        enabled = enabled,
        external = source is PluginSource.External,
        commands = plugin.commands.map { command ->
            command.javaClass.getAnnotation(BotCommand::class.java)?.name ?: command.javaClass.simpleName
        },
        source = when (source) {
            is PluginSource.Builtin -> "builtin"
            is PluginSource.External -> source.originalJar.toString()
        },
        disabledReply = disabledReply,
    )

    private data class PluginSlot(
        val plugin: CommandPlugin,
        var enabled: Boolean,
        var disabledReply: String,
        val source: PluginSource,
    )

    private sealed interface PluginSource {
        data class Builtin(val factory: CommandPluginFactory) : PluginSource
        data class External(
            val originalJar: Path,
            val shadowJar: Path,
            val loader: URLClassLoader,
        ) : PluginSource
    }

    private fun String.isValidPluginId() = matches(Regex("[a-z0-9][a-z0-9._-]*"))

    private fun loadStates() {
        savedStates.clear()
        val file = stateFile()
        if (!Files.exists(file)) return
        val properties = Properties().apply { Files.newInputStream(file).use(::load) }
        properties.stringPropertyNames().map { it.substringBeforeLast('.') }.distinct().forEach { id ->
            savedStates[id] = SavedPluginState(
                enabled = properties.getProperty("$id.enabled")?.toBooleanStrictOrNull() ?: true,
                disabledReply = properties.getProperty("$id.reply").orEmpty(),
            )
        }
    }

    private fun saveState(slot: PluginSlot) {
        savedStates[slot.plugin.id] = SavedPluginState(slot.enabled, slot.disabledReply)
        val properties = Properties()
        savedStates.forEach { (id, state) ->
            properties.setProperty("$id.enabled", state.enabled.toString())
            properties.setProperty("$id.reply", state.disabledReply)
        }
        Files.newOutputStream(stateFile()).use { properties.store(it, "LoMu command plugin state") }
    }

    private fun stateFile() = checkNotNull(pluginDirectory).resolve("plugin-state.properties")

    private data class SavedPluginState(val enabled: Boolean, val disabledReply: String)
    private data class DisabledCommand(val pluginId: String, val command: BotCommand, val reply: String)

    data class DisabledCommandMatch(val pluginId: String, val commandName: String, val reply: String)

    private const val CORE_PLUGIN_ID = "core"
    private const val MAX_PLUGIN_BYTES = 25L * 1024 * 1024
    private const val MAX_DISABLED_REPLY_LENGTH = 1_000
}
