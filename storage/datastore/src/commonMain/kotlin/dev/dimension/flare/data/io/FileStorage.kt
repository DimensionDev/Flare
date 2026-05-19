package dev.dimension.flare.data.io

import okio.FileSystem
import okio.Path

public interface FileStorage {
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

public class OkioFileStorage(
    private val fileSystem: FileSystem,
) : FileStorage {
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

public expect fun defaultFileStorage(): FileStorage
