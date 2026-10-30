package dev.dimension.flare.feature.plugin.installer

import okio.Buffer

internal fun isPluginPath(path: String): Boolean =
    path == FppPaths.MANIFEST || path == FppPaths.ENTRY || path in FppPaths.ICONS ||
        (path.startsWith(FppPaths.LOCALE_PREFIX) && path.endsWith(".json"))

/** Canonical stored ZIP: repository metadata and compression never affect the installed package hash. */
internal fun encodePluginArchive(files: Map<String, ByteArray>): ByteArray {
    val output = Buffer()
    val central = Buffer()
    files.entries.sortedBy { it.key }.forEach { (path, bytes) ->
        val name = path.encodeToByteArray()
        var crc = -1
        bytes.forEach { crc = CRC_TABLE[(crc xor it.toInt()) and 0xff] xor (crc ushr 8) }
        crc = crc.inv()
        val offset = output.size.toInt()
        output
            .writeIntLe(0x04034b50)
            .writeShortLe(20)
            .writeShortLe(0x0800)
            .writeShortLe(0)
            .writeShortLe(0)
            .writeShortLe(33)
            .writeIntLe(crc)
            .writeIntLe(bytes.size)
            .writeIntLe(bytes.size)
            .writeShortLe(name.size)
            .writeShortLe(0)
            .write(name)
            .write(bytes)
        central
            .writeIntLe(0x02014b50)
            .writeShortLe(20)
            .writeShortLe(20)
            .writeShortLe(0x0800)
            .writeShortLe(0)
            .writeShortLe(0)
            .writeShortLe(33)
            .writeIntLe(crc)
            .writeIntLe(bytes.size)
            .writeIntLe(bytes.size)
            .writeShortLe(name.size)
            .writeShortLe(0)
            .writeShortLe(0)
            .writeShortLe(0)
            .writeShortLe(0)
            .writeIntLe(0)
            .writeIntLe(offset)
            .write(name)
    }
    val offset = output.size.toInt()
    val centralSize = central.size.toInt()
    output.writeAll(central)
    output
        .writeIntLe(0x06054b50)
        .writeShortLe(0)
        .writeShortLe(0)
        .writeShortLe(files.size)
        .writeShortLe(files.size)
        .writeIntLe(centralSize)
        .writeIntLe(offset)
        .writeShortLe(0)
    require(output.size <= FppLimits.MAX_PACKAGE_BYTES) { "Plugin package exceeds its size limit" }
    return output.readByteArray()
}

private val CRC_TABLE =
    IntArray(256) { value ->
        var crc = value
        repeat(8) { crc = if (crc and 1 != 0) (crc ushr 1) xor 0xedb88320.toInt() else crc ushr 1 }
        crc
    }
