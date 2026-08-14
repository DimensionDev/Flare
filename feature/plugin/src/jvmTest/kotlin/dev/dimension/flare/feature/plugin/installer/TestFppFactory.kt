package dev.dimension.flare.feature.plugin.installer

import okio.Path
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Base64
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal object TestFppFactory {
    val validManifest: String =
        """
        {
          "schemaVersion": 1,
          "apiVersion": 1,
          "id": "dev.dimension.flare.test.plugin",
          "version": "1.0.0",
          "defaultLocale": "en",
          "name": "Test plugin",
          "platform": {
            "id": "TestPlugin",
            "name": "Test platform",
            "detector": { "priority": 10 },
            "loginMethods": [
              { "id": "oauth", "interaction": "OAuth", "title": "OAuth" }
            ],
            "capabilities": {
              "flare.datasource.timeline/v1": {
                "operations": { "page": { "directions": ["refresh", "older"] } }
              }
            },
            "timelines": [
              { "id": "home", "title": "Home", "defaultForNewAccount": true }
            ]
          }
        }
        """.trimIndent()

    val validScript: String =
        """
        definePlugin({
          detector: { async detect(request) { return { matched: true }; } },
          login: {
            oauth: {
              async begin(request) { return { type: "Pending" }; },
              async resume(request) { return { type: "Pending" }; },
            },
          },
          capabilities: {
            timeline: { async page(request) { return { items: [] }; } },
          },
        });
        """.trimIndent()

    val validPng: ByteArray =
        Base64
            .getDecoder()
            .decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=")

    fun write(
        path: Path,
        entries: List<Pair<String, ByteArray>> = validEntries(),
        stored: Boolean = false,
    ) {
        ZipOutputStream(Files.newOutputStream(Paths.get(path.toString()))).use { zip ->
            entries.forEach { (name, bytes) ->
                val entry =
                    ZipEntry(name).apply {
                        time = 0L
                        if (stored) {
                            method = ZipEntry.STORED
                            size = bytes.size.toLong()
                            compressedSize = size
                            crc = CRC32().apply { update(bytes) }.value
                        }
                    }
                zip.putNextEntry(entry)
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }

    fun validEntries(
        manifest: String = validManifest,
        script: String = validScript,
        icon: ByteArray = validPng,
    ): List<Pair<String, ByteArray>> =
        listOf(
            FppPaths.MANIFEST to manifest.encodeToByteArray(),
            FppPaths.ENTRY to script.encodeToByteArray(),
            FppPaths.ICON to icon,
        )

    fun replaceAscii(
        path: Path,
        from: String,
        to: String,
    ) {
        require(from.length == to.length)
        val bytes = Files.readAllBytes(Paths.get(path.toString()))
        val needle = from.encodeToByteArray()
        val replacement = to.encodeToByteArray()
        for (index in 0..bytes.size - needle.size) {
            if (needle.indices.all { bytes[index + it] == needle[it] }) {
                replacement.copyInto(bytes, index)
            }
        }
        Files.write(Paths.get(path.toString()), bytes)
    }

    fun flipLastByte(path: Path) {
        val bytes = Files.readAllBytes(Paths.get(path.toString()))
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        Files.write(Paths.get(path.toString()), bytes)
    }

    fun patchLittleEndian(
        path: Path,
        signature: Int,
        relativeOffset: Int,
        value: Long,
        byteCount: Int,
        occurrence: Int = 0,
    ) {
        val bytes = Files.readAllBytes(Paths.get(path.toString()))
        var found = -1
        var seen = 0
        for (index in 0..bytes.size - 4) {
            if (bytes.intLe(index) == signature) {
                if (seen++ == occurrence) {
                    found = index
                    break
                }
            }
        }
        require(found >= 0) { "ZIP signature not found" }
        repeat(byteCount) { offset ->
            bytes[found + relativeOffset + offset] = (value ushr (offset * 8)).toByte()
        }
        Files.write(Paths.get(path.toString()), bytes)
    }
}

private fun ByteArray.intLe(offset: Int): Int =
    (this[offset].toInt() and 0xff) or
        ((this[offset + 1].toInt() and 0xff) shl 8) or
        ((this[offset + 2].toInt() and 0xff) shl 16) or
        ((this[offset + 3].toInt() and 0xff) shl 24)
