package dev.dimension.flare.data.io

import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

public interface FileStorage {
    public fun dataStoreFile(fileName: String): Path

    public fun draftMediaFile(
        groupId: String,
        fileName: String,
    ): Path

    public fun createDirectories(path: Path)

    public fun write(
        path: Path,
        bytes: ByteArray,
    )

    public fun read(path: Path): ByteArray

    public fun exists(path: Path): Boolean

    public fun delete(path: Path)

    public fun list(path: Path): List<Path>
}

public class OkioFileStorage internal constructor(
    internal val fileSystem: FileSystem,
    private val platformPathProducer: PlatformPathProducer,
) : FileStorage {
    public constructor(
        fileSystem: FileSystem,
        root: Path,
    ) : this(
        fileSystem = fileSystem,
        platformPathProducer = RootPlatformPathProducer(root),
    )

    override fun dataStoreFile(fileName: String): Path = platformPathProducer.dataStoreFile(fileName)

    override fun draftMediaFile(
        groupId: String,
        fileName: String,
    ): Path = platformPathProducer.draftMediaFile(groupId, fileName)

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

    override fun exists(path: Path): Boolean = fileSystem.exists(path)

    override fun delete(path: Path) {
        fileSystem.delete(path)
    }

    override fun list(path: Path): List<Path> = fileSystem.list(path)
}

public class InMemoryFileStorage(
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

    override fun exists(path: Path): Boolean = path in files || path in directories

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

private class RootPlatformPathProducer(
    private val root: Path,
) : PlatformPathProducer {
    override fun dataStoreFile(fileName: String): Path = root.resolve(fileName)

    override fun draftMediaFile(
        groupId: String,
        fileName: String,
    ): Path =
        root
            .resolve("draft_media")
            .resolve(groupId)
            .resolve(fileName)
}
