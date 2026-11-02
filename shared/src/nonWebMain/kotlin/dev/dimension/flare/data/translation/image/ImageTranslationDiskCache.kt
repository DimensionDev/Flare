package dev.dimension.flare.data.translation.image

import dev.dimension.flare.common.JSON
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import okio.FileSystem
import okio.HashingSink
import okio.Path
import okio.blackholeSink
import okio.buffer
import okio.use
import kotlin.time.Clock
import kotlin.uuid.Uuid

internal class ImageTranslationDiskCache(
    private val fs: FileSystem,
    private val root: Path,
    private val ttlMillis: Long? = null,
    private val maxBytes: Long = 512L * 1024 * 1024,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private val mutex = Mutex()
    private var generation = 0L
    private val retentionMillis get() = ttlMillis ?: ImageTranslationCachePolicy.retentionMillis
    private val ownerGenerations = mutableMapOf<String, Long>()

    data class Ticket(
        val generation: Long,
        val owner: String,
        val ownerGeneration: Long,
    )

    @Serializable
    private data class Manifest(
        val owner: String,
        val image: String,
        val imageHash: String,
        val info: TranslationImageInfo,
        val lastAccess: Long,
    )

    suspend fun ticket(owner: String): Ticket = mutex.withLock { Ticket(generation, owner, ownerGenerations[owner] ?: 0) }

    suspend fun read(
        key: String,
        destination: Path,
    ): TranslationImageInfo? =
        mutex.withLock {
            runCatching { trim() }
            val dir = root / key
            val manifest = manifest(dir) ?: return@withLock null
            if (now() - manifest.lastAccess >= retentionMillis || !fs.exists(dir / manifest.image)) {
                fs.deleteRecursively(dir, mustExist = false)
                return@withLock null
            }
            if (translationFileHash(fs, dir / manifest.image) != manifest.imageHash) {
                fs.deleteRecursively(dir, mustExist = false)
                return@withLock null
            }
            copyTranslationFile(fs, dir / manifest.image, destination)
            runCatching {
                fs.write(dir / "manifest.json") { writeUtf8(JSON.encodeToString(Manifest.serializer(), manifest.copy(lastAccess = now()))) }
            }
            manifest.info
        }

    suspend fun put(
        ticket: Ticket,
        key: String,
        file: Path,
        info: TranslationImageInfo,
    ) = mutex.withLock {
        if (ticket.generation != generation || ticket.ownerGeneration != (ownerGenerations[ticket.owner] ?: 0)) return@withLock
        val size = fs.metadata(file).size ?: return@withLock
        if (size > maxBytes) return@withLock
        fs.createDirectories(root)
        val temp = root / (Uuid.random().toString() + ".part")
        val dir = root / key
        try {
            fs.createDirectories(temp)
            val image = "image." + info.extension
            copyTranslationFile(fs, file, temp / image)
            val manifest = Manifest(ticket.owner, image, translationFileHash(fs, file), info, now())
            fs.write(temp / "manifest.json") { writeUtf8(JSON.encodeToString(Manifest.serializer(), manifest)) }
            fs.deleteRecursively(dir, mustExist = false)
            fs.atomicMove(temp, dir)
            trim()
        } finally {
            fs.deleteRecursively(temp, mustExist = false)
        }
    }

    suspend fun clear(owner: String? = null) =
        mutex.withLock {
            if (owner == null) generation++ else ownerGenerations[owner] = (ownerGenerations[owner] ?: 0) + 1
            fs.listOrNull(root).orEmpty().forEach { dir ->
                if (owner == null || manifest(dir)?.owner == owner) fs.deleteRecursively(dir, mustExist = false)
            }
        }

    suspend fun size(): Long =
        mutex.withLock {
            trim()
            fs.listOrNull(root).orEmpty().sumOf(::entrySize)
        }

    private fun manifest(dir: Path): Manifest? =
        try {
            fs.read(dir / "manifest.json") { JSON.decodeFromString(Manifest.serializer(), readUtf8()) }.takeIf {
                it.image == "image." + it.info.extension && it.info.width > 0 && it.info.height > 0
            }
        } catch (_: Exception) {
            null
        }

    private fun entrySize(dir: Path): Long = fs.listOrNull(dir).orEmpty().sumOf { fs.metadataOrNull(it)?.size ?: 0L }

    private fun trim() {
        val entries =
            fs
                .listOrNull(root)
                .orEmpty()
                .mapNotNull { dir ->
                    val manifest = manifest(dir)
                    if (manifest == null || now() - manifest.lastAccess >= retentionMillis || !fs.exists(dir / manifest.image)) {
                        fs.deleteRecursively(dir, mustExist = false)
                        null
                    } else {
                        Triple(dir, manifest.lastAccess, entrySize(dir))
                    }
                }.sortedBy { it.second }
        var size = entries.sumOf { it.third }
        for ((dir, _, bytes) in entries) {
            if (size <= maxBytes) break
            fs.deleteRecursively(dir, mustExist = false)
            size -= bytes
        }
    }
}

internal fun translationFileHash(
    fs: FileSystem,
    file: Path,
): String {
    val hash = HashingSink.sha256(blackholeSink())
    fs.source(file).buffer().use { it.readAll(hash) }
    return hash.hash.hex()
}

internal fun copyTranslationFile(
    fs: FileSystem,
    source: Path,
    destination: Path,
) {
    fs.createDirectories(requireNotNull(destination.parent))
    fs.source(source).buffer().use { input -> fs.sink(destination).buffer().use { it.writeAll(input) } }
}
