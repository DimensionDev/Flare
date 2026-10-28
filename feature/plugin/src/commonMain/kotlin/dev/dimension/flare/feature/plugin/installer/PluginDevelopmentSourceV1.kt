package dev.dimension.flare.feature.plugin.installer

import okio.Buffer
import okio.FileSystem
import okio.Path
import okio.buffer
import okio.use

internal data class PluginDevelopmentSourceV1(
    val archive: FppArchive,
    val hash: String,
) {
    companion object {
        fun read(
            fileSystem: FileSystem,
            root: Path,
        ): PluginDevelopmentSourceV1 {
            fun directory(path: Path) {
                val metadata = fileSystem.metadata(path)
                require(metadata.isDirectory && metadata.symlinkTarget == null) { "Plugin directory must not be a symbolic link" }
            }
            directory(root)
            directory(root / "assets")
            val names = mutableListOf(FppPaths.MANIFEST, FppPaths.ENTRY, FppPaths.ICON)
            val locales = root / "locales"
            if (fileSystem.exists(locales)) {
                directory(locales)
                val catalogs = fileSystem.list(locales).filter { it.name.endsWith(".json") }
                require(catalogs.size <= FppLimits.MAX_CATALOG_COUNT) { "Too many locale catalogs" }
                catalogs.forEach { names += "locales/${it.name}" }
            }
            val entries =
                names.sorted().map { name ->
                    val metadata = fileSystem.metadata(root / name)
                    require(metadata.isRegularFile && metadata.symlinkTarget == null) { "Plugin files must not be symbolic links" }
                    val size = requireNotNull(metadata.size)
                    require(size >= 0) { "Invalid plugin file size" }
                    FppArchiveEntry(name, false, size, size, 0)
                }
            FppArchiveReader(fileSystem).validateLayout(entries)
            val files =
                entries.associate { entry ->
                    val bytes =
                        fileSystem.source(root / entry.path).buffer().use { source ->
                            val buffer = Buffer()
                            while (true) {
                                val remaining = entry.uncompressedSize + 1 - buffer.size
                                require(remaining > 0) { "Plugin file changed while reading" }
                                if (source.read(buffer, minOf(8192L, remaining)) == -1L) break
                            }
                            require(buffer.size == entry.uncompressedSize) { "Plugin file changed while reading" }
                            buffer.readByteArray()
                        }
                    entry.path to bytes
                }
            val digest = Buffer()
            files.forEach { (name, bytes) ->
                digest
                    .writeUtf8(name)
                    .writeByte(0)
                    .writeLong(bytes.size.toLong())
                    .write(bytes)
            }
            return PluginDevelopmentSourceV1(FppArchive(entries, files), digest.readByteString().sha256().hex())
        }
    }
}
