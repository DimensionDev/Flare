package dev.dimension.flare.data.io

import okio.Path
import okio.Path.Companion.toPath

internal class FakeFileStorage(
    private val root: Path = "/flare-test".toPath(),
    private val onCreateDirectories: (Path) -> Unit = {},
    private val onWrite: (Path, ByteArray) -> Unit = { _, _ -> },
    private val onRead: (Path) -> Unit = {},
    private val onDelete: (Path) -> Unit = {},
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
        onCreateDirectories(path)
        generateSequence(path) { it.parent }
            .forEach { directories += it }
    }

    override fun write(
        path: Path,
        bytes: ByteArray,
    ) {
        onWrite(path, bytes)
        path.parent?.let(::createDirectories)
        files[path] = bytes.copyOf()
    }

    override fun read(path: Path): ByteArray {
        onRead(path)
        return checkNotNull(files[path]) { "File not found: $path" }
            .copyOf()
    }

    override fun exists(path: Path): Boolean = path in files || path in directories

    override fun delete(path: Path) {
        onDelete(path)
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
