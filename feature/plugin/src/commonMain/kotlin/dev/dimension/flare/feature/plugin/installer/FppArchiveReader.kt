package dev.dimension.flare.feature.plugin.installer

import okio.Buffer
import okio.BufferedSource
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import okio.openZip
import okio.use

internal data class FppArchiveEntry(
    val path: String,
    val isDirectory: Boolean,
    val compressedSize: Long,
    val uncompressedSize: Long,
    val localHeaderOffset: Long,
    val dataOffset: Long = 0,
)

internal data class FppArchive(
    val entries: List<FppArchiveEntry>,
    val files: Map<String, ByteArray>,
)

internal class FppArchiveReader(
    private val fileSystem: FileSystem,
) {
    fun read(path: Path): FppArchive {
        val packageSize = requireNotNull(fileSystem.metadataOrNull(path)?.size) { "Package does not exist" }
        require(packageSize in 1..FppLimits.MAX_PACKAGE_BYTES) { "Package exceeds the compressed size limit" }

        val entries = readDirectory(path, packageSize)
        validateLayout(entries)
        val zip = fileSystem.openZip(path)
        val files =
            entries
                .asSequence()
                .filterNot(FppArchiveEntry::isDirectory)
                .associate { entry ->
                    val bytes =
                        zip.source("/${entry.path}".toPath()).buffer().use { source ->
                            val buffer = Buffer()
                            while (true) {
                                val remaining = entry.uncompressedSize + 1 - buffer.size
                                require(remaining > 0) { "${entry.path} exceeds its declared uncompressed size" }
                                val read = source.read(buffer, minOf(COPY_BUFFER_BYTES, remaining))
                                if (read == -1L) break
                            }
                            require(buffer.size == entry.uncompressedSize) {
                                "Invalid uncompressed size for ${entry.path}"
                            }
                            buffer.readByteArray()
                        }
                    entry.path to bytes
                }
        return FppArchive(entries = entries, files = files)
    }

    private fun readDirectory(
        path: Path,
        packageSize: Long,
    ): List<FppArchiveEntry> =
        fileSystem.openReadOnly(path).use { handle ->
            val tailSize = minOf(packageSize, MAX_EOCD_SEARCH_BYTES).toInt()
            val tailOffset = packageSize - tailSize
            val tail = handle.source(tailOffset).buffer().use { it.readByteArray(tailSize.toLong()) }
            val eocdIndex = tail.findEocd()
            val eocdOffset = tailOffset + eocdIndex
            require(eocdIndex + EOCD_FIXED_BYTES <= tail.size) { "Truncated ZIP end record" }
            val commentSize = tail.ushortLe(eocdIndex + 20)
            require(eocdIndex + EOCD_FIXED_BYTES + commentSize == tail.size) { "ZIP comments or trailing data are not supported" }
            require(tail.ushortLe(eocdIndex + 4) == 0 && tail.ushortLe(eocdIndex + 6) == 0) { "Spanned ZIP is not supported" }
            val entryCount = tail.ushortLe(eocdIndex + 10)
            require(entryCount == tail.ushortLe(eocdIndex + 8)) { "Spanned ZIP is not supported" }
            require(entryCount in 1..FppLimits.MAX_ENTRY_COUNT) { "Invalid ZIP entry count" }
            val centralSize = tail.uintLe(eocdIndex + 12)
            val centralOffset = tail.uintLe(eocdIndex + 16)
            require(centralSize != UINT32_MAX && centralOffset != UINT32_MAX) { "ZIP64 is not supported" }
            require(centralOffset + centralSize == eocdOffset) { "Invalid ZIP central directory bounds" }
            if (eocdOffset >= ZIP64_LOCATOR_BYTES) {
                val locator = handle.source(eocdOffset - ZIP64_LOCATOR_BYTES).buffer().use { it.readIntLe() }
                require(locator != ZIP64_LOCATOR_SIGNATURE) { "ZIP64 is not supported" }
            }

            val source = handle.source(centralOffset).buffer()
            source.use {
                val result = ArrayList<FppArchiveEntry>(entryCount)
                var consumed = 0L
                repeat(entryCount) {
                    val parsed = source.readCentralEntry()
                    consumed += parsed.bytesRead
                    require(consumed <= centralSize) { "Central directory exceeds its declared size" }
                    val dataOffset = validateLocalHeader(handle.source(parsed.entry.localHeaderOffset).buffer(), parsed)
                    result += parsed.entry.copy(dataOffset = dataOffset)
                }
                require(consumed == centralSize) { "Central directory size mismatch" }
                validateEntryRanges(result, centralOffset)
                result
            }
        }

    private fun BufferedSource.readCentralEntry(): ParsedCentralEntry {
        require(readIntLe() == CENTRAL_HEADER_SIGNATURE) { "Invalid ZIP central directory entry" }
        val versionMadeBy = ushortLe()
        skip(2)
        val flags = ushortLe()
        val compression = ushortLe()
        skip(4)
        skip(4)
        val compressedSize = uintLe()
        val uncompressedSize = uintLe()
        val nameSize = ushortLe()
        val extraSize = ushortLe()
        val commentSize = ushortLe()
        val disk = ushortLe()
        skip(2)
        val externalAttributes = uintLe()
        val localHeaderOffset = uintLe()

        require(disk == 0) { "Spanned ZIP is not supported" }
        require(compressedSize != UINT32_MAX && uncompressedSize != UINT32_MAX && localHeaderOffset != UINT32_MAX) {
            "ZIP64 is not supported"
        }
        validateFlags(flags, compression)
        require(compression == COMPRESSION_STORED || compression == COMPRESSION_DEFLATE) {
            "Unsupported ZIP compression method"
        }
        val rawName = readByteArray(nameSize.toLong())
        val name = rawName.decodeToString(throwOnInvalidSequence = true)
        val extra = readByteArray(extraSize.toLong())
        validateExtra(extra)
        skip(commentSize.toLong())
        validatePath(name)

        val isDirectory = name.endsWith('/')
        validateUnixType(versionMadeBy, externalAttributes, isDirectory)
        if (isDirectory) {
            require(compressedSize == 0L && uncompressedSize == 0L) { "Directory entry contains data" }
        }
        return ParsedCentralEntry(
            entry =
                FppArchiveEntry(
                    path = name.removeSuffix("/"),
                    isDirectory = isDirectory,
                    compressedSize = compressedSize,
                    uncompressedSize = uncompressedSize,
                    localHeaderOffset = localHeaderOffset,
                ),
            rawName = rawName,
            flags = flags,
            compression = compression,
            bytesRead = CENTRAL_FIXED_BYTES + nameSize + extraSize + commentSize.toLong(),
        )
    }

    private fun validateLocalHeader(
        source: BufferedSource,
        central: ParsedCentralEntry,
    ): Long =
        source.use {
            require(source.readIntLe() == LOCAL_HEADER_SIGNATURE) { "Invalid ZIP local header" }
            source.skip(2)
            val flags = source.ushortLe()
            val compression = source.ushortLe()
            source.skip(4)
            source.skip(4)
            val compressedSize = source.uintLe()
            val uncompressedSize = source.uintLe()
            val nameSize = source.ushortLe()
            val extraSize = source.ushortLe()
            val rawName = source.readByteArray(nameSize.toLong())
            val extra = source.readByteArray(extraSize.toLong())

            validateFlags(flags, compression)
            require(flags == central.flags && compression == central.compression) { "ZIP header mismatch" }
            require(rawName.contentEquals(central.rawName)) { "ZIP entry name mismatch" }
            validateExtra(extra)
            if (flags and FLAG_DATA_DESCRIPTOR == 0) {
                require(
                    compressedSize == central.entry.compressedSize &&
                        uncompressedSize == central.entry.uncompressedSize,
                ) { "ZIP entry size mismatch" }
            }
            central.entry.localHeaderOffset + LOCAL_FIXED_BYTES + nameSize + extraSize
        }

    private fun validateLayout(entries: List<FppArchiveEntry>) {
        val paths = entries.map(FppArchiveEntry::path)
        require(paths.distinct().size == paths.size) { "Duplicate ZIP path" }
        require(paths.map(String::lowercase).distinct().size == paths.size) { "Case-conflicting ZIP path" }
        entries.filter(FppArchiveEntry::isDirectory).forEach { entry ->
            require(entry.path == "assets" || entry.path == "locales") {
                "Unsupported package directory: ${entry.path}"
            }
        }

        val files = entries.filterNot(FppArchiveEntry::isDirectory)
        val required = setOf(FppPaths.MANIFEST, FppPaths.ENTRY, FppPaths.ICON)
        require(files.map(FppArchiveEntry::path).containsAll(required)) { "Package is missing a required file" }
        files.forEach { entry ->
            val limit =
                when {
                    entry.path == FppPaths.MANIFEST -> {
                        FppLimits.MAX_MANIFEST_BYTES
                    }

                    entry.path == FppPaths.ENTRY -> {
                        FppLimits.MAX_SCRIPT_BYTES
                    }

                    entry.path == FppPaths.ICON -> {
                        FppLimits.MAX_ICON_BYTES
                    }

                    entry.path.startsWith(FppPaths.LOCALE_PREFIX) && entry.path.endsWith(".json") -> {
                        FppLimits.MAX_CATALOG_BYTES
                    }

                    else -> {
                        error("Unsupported package path: ${entry.path}")
                    }
                }
            require(entry.uncompressedSize <= limit) { "${entry.path} exceeds its size limit" }
        }
        require(files.count { it.path.startsWith(FppPaths.LOCALE_PREFIX) } <= FppLimits.MAX_CATALOG_COUNT) {
            "Too many locale catalogs"
        }
        require(files.sumOf(FppArchiveEntry::uncompressedSize) <= FppLimits.MAX_UNCOMPRESSED_BYTES) {
            "Package exceeds the uncompressed size limit"
        }
    }

    private fun validateEntryRanges(
        entries: List<FppArchiveEntry>,
        centralOffset: Long,
    ) {
        val sorted = entries.sortedBy(FppArchiveEntry::localHeaderOffset)
        require(sorted.firstOrNull()?.localHeaderOffset == 0L) { "ZIP preambles are not supported" }
        sorted.forEachIndexed { index, entry ->
            val dataEnd = entry.dataOffset + entry.compressedSize
            val boundary = sorted.getOrNull(index + 1)?.localHeaderOffset ?: centralOffset
            require(entry.localHeaderOffset < entry.dataOffset && dataEnd <= boundary) { "Overlapping ZIP entries" }
        }
    }

    private fun validatePath(name: String) {
        require(name.isNotEmpty() && name.length <= MAX_PATH_LENGTH) { "Invalid ZIP path" }
        require(name.all { it.code in 0x20..0x7e }) { "ZIP paths must be ASCII" }
        require(!name.startsWith('/') && '\\' !in name && !DRIVE_PATH.containsMatchIn(name)) { "Absolute ZIP path" }
        val normalized = name.removeSuffix("/")
        require(normalized.isNotEmpty()) { "Invalid ZIP path" }
        val segments = normalized.split('/')
        require(segments.all { it.isNotEmpty() && it != "." && it != ".." }) { "ZIP path traversal" }
    }

    private fun validateFlags(
        flags: Int,
        compression: Int,
    ) {
        require(flags and (FLAG_ENCRYPTED or FLAG_STRONG_ENCRYPTION) == 0) { "Encrypted ZIP entries are not supported" }
        require(flags and ALLOWED_FLAGS.inv() == 0) { "Unsupported ZIP entry flags" }
        require(compression == COMPRESSION_DEFLATE || flags and FLAG_DEFLATE_OPTIONS == 0) {
            "Invalid ZIP compression flags"
        }
    }

    private fun validateUnixType(
        versionMadeBy: Int,
        externalAttributes: Long,
        directory: Boolean,
    ) {
        if (versionMadeBy ushr 8 != UNIX_HOST) return
        val type = ((externalAttributes ushr 16).toInt()) and UNIX_TYPE_MASK
        require(type != UNIX_SYMLINK) { "Symbolic links are not supported" }
        if (type != 0) {
            require(type == if (directory) UNIX_DIRECTORY else UNIX_REGULAR) { "Unsupported UNIX file type" }
        }
    }

    private fun validateExtra(extra: ByteArray) {
        val buffer = Buffer().write(extra)
        while (!buffer.exhausted()) {
            require(buffer.size >= 4L) { "Invalid ZIP extra field" }
            val id = buffer.ushortLe()
            val size = buffer.ushortLe()
            require(buffer.size >= size) { "Invalid ZIP extra field" }
            require(id != ZIP64_EXTRA_ID) { "ZIP64 is not supported" }
            buffer.skip(size.toLong())
        }
    }
}

internal object FppPaths {
    const val MANIFEST: String = "manifest.json"
    const val ENTRY: String = "plugin.js"
    const val ICON: String = "assets/icon.png"
    const val LOCALE_PREFIX: String = "locales/"
}

internal object FppLimits {
    const val MAX_PACKAGE_BYTES: Long = 20L * 1024 * 1024
    const val MAX_UNCOMPRESSED_BYTES: Long = 50L * 1024 * 1024
    const val MAX_ENTRY_COUNT: Int = 256
    const val MAX_MANIFEST_BYTES: Long = 256L * 1024
    const val MAX_SCRIPT_BYTES: Long = 4L * 1024 * 1024
    const val MAX_ICON_BYTES: Long = 2L * 1024 * 1024
    const val MAX_CATALOG_BYTES: Long = 256L * 1024
    const val MAX_CATALOG_COUNT: Int = 32
    const val MAX_INDEX_BYTES: Long = 1024L * 1024
    const val MAX_PLUGIN_COUNT: Int = 32
}

private data class ParsedCentralEntry(
    val entry: FppArchiveEntry,
    val rawName: ByteArray,
    val flags: Int,
    val compression: Int,
    val bytesRead: Long,
)

private fun BufferedSource.ushortLe(): Int = readShortLe().toInt() and 0xffff

private fun BufferedSource.uintLe(): Long = readIntLe().toLong() and UINT32_MAX

private fun ByteArray.findEocd(): Int {
    for (index in size - EOCD_FIXED_BYTES downTo 0) {
        if (intLe(index) == EOCD_SIGNATURE) return index
    }
    error("ZIP end record not found")
}

private fun ByteArray.ushortLe(offset: Int): Int = (this[offset].toInt() and 0xff) or ((this[offset + 1].toInt() and 0xff) shl 8)

private fun ByteArray.uintLe(offset: Int): Long = intLe(offset).toLong() and UINT32_MAX

private fun ByteArray.intLe(offset: Int): Int =
    (this[offset].toInt() and 0xff) or
        ((this[offset + 1].toInt() and 0xff) shl 8) or
        ((this[offset + 2].toInt() and 0xff) shl 16) or
        ((this[offset + 3].toInt() and 0xff) shl 24)

private const val EOCD_SIGNATURE = 0x06054b50
private const val ZIP64_LOCATOR_SIGNATURE = 0x07064b50
private const val CENTRAL_HEADER_SIGNATURE = 0x02014b50
private const val LOCAL_HEADER_SIGNATURE = 0x04034b50
private const val ZIP64_EXTRA_ID = 0x0001
private const val EOCD_FIXED_BYTES = 22
private const val ZIP64_LOCATOR_BYTES = 20L
private const val MAX_EOCD_SEARCH_BYTES = 65_557L
private const val CENTRAL_FIXED_BYTES = 46L
private const val LOCAL_FIXED_BYTES = 30L
private const val UINT32_MAX = 0xffff_ffffL
private const val COMPRESSION_STORED = 0
private const val COMPRESSION_DEFLATE = 8
private const val FLAG_ENCRYPTED = 1
private const val FLAG_DEFLATE_OPTIONS = (1 shl 1) or (1 shl 2)
private const val FLAG_DATA_DESCRIPTOR = 1 shl 3
private const val FLAG_STRONG_ENCRYPTION = 1 shl 6
private const val FLAG_UTF8 = 1 shl 11
private const val ALLOWED_FLAGS = FLAG_DEFLATE_OPTIONS or FLAG_DATA_DESCRIPTOR or FLAG_UTF8
private const val UNIX_HOST = 3
private const val UNIX_TYPE_MASK = 0xf000
private const val UNIX_REGULAR = 0x8000
private const val UNIX_DIRECTORY = 0x4000
private const val UNIX_SYMLINK = 0xa000
private const val MAX_PATH_LENGTH = 512
private const val COPY_BUFFER_BYTES = 8_192L
private val DRIVE_PATH = Regex("^[A-Za-z]:")
