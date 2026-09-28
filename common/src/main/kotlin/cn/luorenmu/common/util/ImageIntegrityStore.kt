package cn.luorenmu.common.util

import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Properties
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/** Keeps verified image metadata so unchanged files do not need to be decoded on every startup. */
class ImageIntegrityStore(
    resourcesRoot: Path,
    private val manifest: Path,
    private val validator: (Path, ByteArray) -> Boolean = ::isCompleteImage,
) {
    private val root = resourcesRoot.toAbsolutePath().normalize()
    private val records = ConcurrentHashMap<String, Stamp>()
    private val manifestLock = Any()

    init {
        if (Files.isRegularFile(manifest)) {
            val properties = Properties()
            runCatching { Files.newInputStream(manifest).use(properties::load) }
            properties.forEach { (key, value) ->
                val parts = value.toString().split(':')
                if (parts.size == 2) {
                    val size = parts[0].toLongOrNull()
                    val modified = parts[1].toLongOrNull()
                    if (size != null && modified != null) records[key.toString()] = Stamp(size, modified)
                }
            }
        }
    }

    data class ScanResult(val checked: Int, val skipped: Int, val invalid: Int)
    private data class Stamp(val size: Long, val modifiedNanos: Long)

    fun scanExistingImages(): ScanResult {
        val imageRoot = root.resolve("images")
        if (!Files.isDirectory(imageRoot)) return ScanResult(0, 0, 0)
        val originalRecords = records.toMap()
        var checked = 0
        var skipped = 0
        var invalid = 0
        val found = mutableSetOf<String>()
        Files.walk(imageRoot).use { paths ->
            paths.filter(Files::isRegularFile).filter(::isSupportedImage).forEach { path ->
                val relative = checkNotNull(key(path))
                found.add(relative)
                val current = stamp(path)
                if (current != null && records[relative] == current) {
                    skipped++
                } else {
                    checked++
                    if (!validateAndRemember(path, persist = false)) invalid++
                }
            }
        }
        synchronized(manifestLock) {
            records.keys.removeIf { it.startsWith("images/") && it !in found }
            if (records != originalRecords || !Files.isRegularFile(manifest)) persist()
        }
        return ScanResult(checked, skipped, invalid)
    }

    fun check(resource: Path): Boolean {
        val path = resource.toAbsolutePath().normalize()
        val current = stamp(path)
        val relative = key(path)
        if (current == null) {
            if (relative != null) forget(relative)
            return false
        }
        if (relative != null && records[relative] == current) return true
        return validateAndRemember(path, persist = true)
    }

    /** Validate the received bytes and the temporary file before replacing the destination. */
    fun installVerified(resource: Path, bytes: ByteArray) {
        val target = resource.toAbsolutePath().normalize()
        require(validator(target, bytes)) { "Downloaded image is incomplete: $target" }
        Files.createDirectories(target.parent)
        val temporary = target.resolveSibling(".${target.fileName}.${UUID.randomUUID()}.part")
        try {
            Files.write(temporary, bytes)
            require(validator(target, Files.readAllBytes(temporary))) { "Saved image is incomplete: $target" }
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
            }
            key(target)?.let { record(it, checkNotNull(stamp(target))) }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    fun invalidate(resource: Path) { key(resource.toAbsolutePath().normalize())?.let(::forget) }
    fun recordCount(): Int = records.size
    fun clearMemory() = records.clear()

    private fun validateAndRemember(path: Path, persist: Boolean): Boolean {
        val valid = runCatching { validator(path, Files.readAllBytes(path)) }.getOrDefault(false)
        val relative = key(path)
        if (relative != null) {
            if (valid) {
                val current = stamp(path) ?: return false
                if (persist) record(relative, current) else records[relative] = current
            } else if (persist) forget(relative) else records.remove(relative)
        }
        return valid
    }

    private fun key(path: Path): String? = path.takeIf { it.startsWith(root) }
        ?.let { root.relativize(it).toString().replace('\\', '/') }

    private fun stamp(path: Path): Stamp? = runCatching {
        if (!Files.isRegularFile(path)) null else Stamp(Files.size(path), Files.getLastModifiedTime(path).to(TimeUnit.NANOSECONDS))
    }.getOrNull()

    private fun record(key: String, stamp: Stamp) = synchronized(manifestLock) {
        records[key] = stamp
        persist()
    }

    private fun forget(key: String) = synchronized(manifestLock) {
        if (records.remove(key) != null) persist()
    }

    private fun persist() {
        manifest.parent?.let(Files::createDirectories)
        val properties = Properties()
        records.forEach { (key, stamp) -> properties.setProperty(key, "${stamp.size}:${stamp.modifiedNanos}") }
        val temporary = manifest.resolveSibling(".${manifest.fileName}.${UUID.randomUUID()}.part")
        try {
            Files.newOutputStream(temporary).use { properties.store(it, "Verified image resources") }
            try {
                Files.move(temporary, manifest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temporary, manifest, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
}

private fun isSupportedImage(path: Path): Boolean = path.fileName.toString().substringAfterLast('.', "").lowercase() in
    setOf("png", "jpg", "jpeg", "svg")

private fun isCompleteImage(path: Path, bytes: ByteArray): Boolean {
    if (bytes.isEmpty()) return false
    return when (path.fileName.toString().substringAfterLast('.', "").lowercase()) {
        "png" -> bytes.size >= 20 && bytes.take(8).toByteArray().contentEquals(
            byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a),
        ) && bytes.copyOfRange(bytes.size - 8, bytes.size - 4).decodeToString() == "IEND" && decodesRaster(bytes)
        "jpg", "jpeg" -> bytes.size >= 4 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() &&
            bytes[bytes.size - 2] == 0xff.toByte() && bytes.last() == 0xd9.toByte() && decodesRaster(bytes)
        "svg" -> runCatching {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
                setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
                isXIncludeAware = false
                isExpandEntityReferences = false
            }
            factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes)).documentElement.localName == "svg"
        }.getOrDefault(false)
        else -> false
    }
}

private fun decodesRaster(bytes: ByteArray): Boolean = runCatching {
    ImageIO.read(ByteArrayInputStream(bytes))?.let { it.width > 0 && it.height > 0 } ?: false
}.getOrDefault(false)
