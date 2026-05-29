package dev.dimension.flare

import okio.Buffer
import okio.FileHandle
import okio.FileMetadata
import okio.FileNotFoundException
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.Sink
import okio.Source
import okio.Timeout

private class FileState(
    var bytes: ByteArray,
)

internal class InMemoryTestFileSystem : FileSystem() {
    private val directories = mutableSetOf("/".toPath())
    private val files = mutableMapOf<Path, FileState>()

    override fun canonicalize(path: Path): Path {
        if (!exists(path)) {
            throw FileNotFoundException("no such file: $path")
        }
        return path
    }

    override fun metadataOrNull(path: Path): FileMetadata? =
        when {
            path in directories -> {
                FileMetadata(isDirectory = true)
            }

            path in files -> {
                FileMetadata(
                    isRegularFile = true,
                    size =
                        files
                            .getValue(path)
                            .bytes.size
                            .toLong(),
                )
            }

            else -> {
                null
            }
        }

    override fun list(dir: Path): List<Path> = listOrNull(dir) ?: throw FileNotFoundException("no such directory: $dir")

    override fun listOrNull(dir: Path): List<Path>? {
        if (dir !in directories) {
            return null
        }
        return (directories.asSequence() + files.keys.asSequence())
            .filter { it != dir && it.parent == dir }
            .distinct()
            .sorted()
            .toList()
    }

    override fun openReadOnly(file: Path): FileHandle {
        if (file !in files) {
            throw FileNotFoundException("no such file: $file")
        }
        return InMemoryFileHandle(file, readWrite = false)
    }

    override fun openReadWrite(
        file: Path,
        mustCreate: Boolean,
        mustExist: Boolean,
    ): FileHandle {
        ensureWritableFile(file, mustCreate, mustExist)
        return InMemoryFileHandle(file, readWrite = true)
    }

    override fun source(file: Path): Source {
        val bytes = files[file]?.bytes ?: throw FileNotFoundException("no such file: $file")
        return Buffer().write(bytes)
    }

    override fun sink(
        file: Path,
        mustCreate: Boolean,
    ): Sink {
        ensureWritableFile(file, mustCreate = mustCreate, mustExist = false)
        return InMemorySink(file)
    }

    override fun appendingSink(
        file: Path,
        mustExist: Boolean,
    ): Sink {
        ensureWritableFile(file, mustCreate = false, mustExist = mustExist)
        return InMemorySink(file, append = true)
    }

    override fun createDirectory(
        dir: Path,
        mustCreate: Boolean,
    ) {
        if (dir in files) {
            throw IOException("file already exists: $dir")
        }
        if (dir in directories) {
            if (mustCreate) {
                throw IOException("directory already exists: $dir")
            }
            return
        }
        val parent = dir.parent
        if (parent != null && parent !in directories) {
            throw IOException("parent is not a directory: $parent")
        }
        directories += dir
    }

    override fun atomicMove(
        source: Path,
        target: Path,
    ) {
        val file = files.remove(source)
        if (file != null) {
            target.parent?.let { parent ->
                if (parent !in directories) {
                    throw IOException("parent is not a directory: $parent")
                }
            }
            files[target] = file
            return
        }
        if (source in directories) {
            throw IOException("directory moves are not supported: $source")
        }
        throw FileNotFoundException("no such file: $source")
    }

    override fun delete(
        path: Path,
        mustExist: Boolean,
    ) {
        if (files.remove(path) != null) {
            return
        }
        if (path in directories) {
            val hasChildren = directories.any { it.parent == path } || files.keys.any { it.parent == path }
            if (hasChildren) {
                throw IOException("directory is not empty: $path")
            }
            directories -= path
            return
        }
        if (mustExist) {
            throw FileNotFoundException("no such file: $path")
        }
    }

    override fun createSymlink(
        source: Path,
        target: Path,
    ): Unit = throw IOException("symlinks are not supported")

    override fun close() = Unit

    private fun ensureWritableFile(
        file: Path,
        mustCreate: Boolean,
        mustExist: Boolean,
    ) {
        if (file in directories) {
            throw IOException("path is a directory: $file")
        }
        val exists = file in files
        if (mustCreate && exists) {
            throw IOException("file already exists: $file")
        }
        if (mustExist && !exists) {
            throw FileNotFoundException("no such file: $file")
        }
        val parent = file.parent
        if (parent != null && parent !in directories) {
            throw IOException("parent is not a directory: $parent")
        }
    }

    private inner class InMemorySink(
        private val file: Path,
        append: Boolean = false,
    ) : Sink {
        private val buffer =
            Buffer().apply {
                if (append) {
                    files[file]?.bytes?.let(::write)
                }
            }
        private var closed = false

        override fun write(
            source: Buffer,
            byteCount: Long,
        ) {
            check(!closed) { "closed" }
            buffer.write(source, byteCount)
        }

        override fun flush() = Unit

        override fun timeout(): Timeout = Timeout.NONE

        override fun close() {
            if (closed) {
                return
            }
            closed = true
            files[file] = FileState(buffer.readByteArray())
        }
    }

    private inner class InMemoryFileHandle(
        private val file: Path,
        readWrite: Boolean,
    ) : FileHandle(readWrite) {
        override fun protectedRead(
            fileOffset: Long,
            array: ByteArray,
            arrayOffset: Int,
            byteCount: Int,
        ): Int {
            val bytes = files[file]?.bytes ?: throw FileNotFoundException("no such file: $file")
            if (fileOffset >= bytes.size) {
                return -1
            }
            val readCount = minOf(byteCount, bytes.size - fileOffset.toInt())
            bytes.copyInto(
                destination = array,
                destinationOffset = arrayOffset,
                startIndex = fileOffset.toInt(),
                endIndex = fileOffset.toInt() + readCount,
            )
            return readCount
        }

        override fun protectedWrite(
            fileOffset: Long,
            array: ByteArray,
            arrayOffset: Int,
            byteCount: Int,
        ) {
            val current = files[file]?.bytes ?: ByteArray(0)
            val offset = fileOffset.toInt()
            val requiredSize = offset + byteCount
            val next =
                if (current.size >= requiredSize) {
                    current.copyOf()
                } else {
                    current.copyOf(requiredSize)
                }
            array.copyInto(
                destination = next,
                destinationOffset = offset,
                startIndex = arrayOffset,
                endIndex = arrayOffset + byteCount,
            )
            files[file] = FileState(next)
        }

        override fun protectedFlush() = Unit

        override fun protectedResize(size: Long) {
            val current = files[file]?.bytes ?: ByteArray(0)
            files[file] = FileState(current.copyOf(size.toInt()))
        }

        override fun protectedSize(): Long = files[file]?.bytes?.size?.toLong() ?: 0L

        override fun protectedClose() = Unit
    }
}
