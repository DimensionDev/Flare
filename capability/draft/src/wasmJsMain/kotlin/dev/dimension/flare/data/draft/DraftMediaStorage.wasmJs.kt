package dev.dimension.flare.data.draft

import okio.Path

internal actual fun defaultDraftMediaStorage(): DraftMediaStorage = WebDraftMediaStorage

private object WebDraftMediaStorage : DraftMediaStorage {
    private val directories = mutableSetOf<Path>()
    private val files = mutableMapOf<Path, ByteArray>()

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

    override fun read(path: Path): ByteArray = checkNotNull(files[path]) { "Draft media file not found: $path" }.copyOf()

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
