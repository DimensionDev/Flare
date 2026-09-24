package dev.dimension.flare.data.io

import dev.dimension.flare.common.PlatformDispatchers
import dev.dimension.flare.common.UploadMedia
import kotlinx.coroutines.withContext
import okio.Buffer
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import okio.use
import kotlin.uuid.Uuid

internal interface FileStorage {
    fun dataStoreFile(fileName: String): Path

    fun draftMediaFile(
        groupId: String,
        fileName: String,
    ): Path

    fun exists(path: Path): Boolean

    fun createDirectories(path: Path)

    fun write(
        path: Path,
        bytes: ByteArray,
    )

    fun read(path: Path): ByteArray

    suspend fun write(
        path: Path,
        media: UploadMedia,
    )

    fun delete(path: Path)

    fun list(path: Path): List<Path>
}

internal open class OkioFileStorage(
    val fileSystem: FileSystem,
    private val root: Path,
) : FileStorage {
    override fun dataStoreFile(fileName: String): Path = root.resolve(fileName)

    override fun draftMediaFile(
        groupId: String,
        fileName: String,
    ): Path =
        root
            .resolve("draft_media")
            .resolve(groupId)
            .resolve(fileName)

    override fun exists(path: Path): Boolean = fileSystem.exists(path)

    override fun createDirectories(path: Path) {
        fileSystem.createDirectories(path)
    }

    override fun write(
        path: Path,
        bytes: ByteArray,
    ) {
        fileSystem.write(path) {
            write(bytes)
        }
    }

    override fun read(path: Path): ByteArray =
        fileSystem.read(path) {
            readByteArray()
        }

    override suspend fun write(
        path: Path,
        media: UploadMedia,
    ): Unit =
        withContext(PlatformDispatchers.IO) {
            // A restored draft may be its own source. Never truncate it before the copy completes.
            val temporary = checkNotNull(path.parent).resolve(".${Uuid.random()}.part")
            try {
                fileSystem.sink(temporary).buffer().use { sink ->
                    media.forEachChunk { bytes, count -> sink.write(bytes, 0, count) }
                }
                fileSystem.atomicMove(temporary, path)
            } finally {
                fileSystem.delete(temporary, mustExist = false)
            }
        }

    override fun delete(path: Path) {
        fileSystem.delete(path)
    }

    override fun list(path: Path): List<Path> = fileSystem.list(path)
}

internal class InMemoryFileStorage(
    private val root: Path = "/flare".toPath(),
) : FileStorage {
    private val directories = mutableSetOf<Path>()
    private val files = mutableMapOf<Path, ByteArray>()

    override fun dataStoreFile(fileName: String): Path = root.resolve(fileName)

    override fun draftMediaFile(
        groupId: String,
        fileName: String,
    ): Path =
        root
            .resolve("draft_media")
            .resolve(groupId)
            .resolve(fileName)

    override fun exists(path: Path): Boolean = path in files || path in directories

    override fun createDirectories(path: Path) {
        generateSequence(path) { it.parent }
            .forEach { directories += it }
    }

    override fun write(
        path: Path,
        bytes: ByteArray,
    ) {
        path.parent?.let(::createDirectories)
        files[path] = bytes.copyOf()
    }

    override fun read(path: Path): ByteArray =
        checkNotNull(files[path]) { "File not found: $path" }
            .copyOf()

    override suspend fun write(
        path: Path,
        media: UploadMedia,
    ) {
        val buffer = Buffer()
        media.forEachChunk { bytes, count -> buffer.write(bytes, 0, count) }
        write(path, buffer.readByteArray())
    }

    override fun delete(path: Path) {
        files -= path
        directories -= path
    }

    override fun list(path: Path): List<Path> =
        (files.keys.asSequence() + directories.asSequence())
            .filter { it.parent == path }
            .distinct()
            .toList()
            .sorted()
}
