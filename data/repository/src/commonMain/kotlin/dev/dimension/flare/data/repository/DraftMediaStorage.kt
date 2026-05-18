package dev.dimension.flare.data.repository

import okio.FileSystem
import okio.Path

internal interface DraftMediaStorage {
    fun createDirectories(path: Path)

    fun write(
        path: Path,
        bytes: ByteArray,
    )

    fun read(path: Path): ByteArray

    fun exists(path: Path): Boolean

    fun delete(path: Path)

    fun list(path: Path): List<Path>
}

internal class FileSystemDraftMediaStorage(
    private val fileSystem: FileSystem,
) : DraftMediaStorage {
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

internal expect fun defaultDraftMediaStorage(): DraftMediaStorage
