package dev.dimension.flare.buildlogic

import java.io.File
import java.net.URI
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.security.MessageDigest

internal fun downloadArchive(
    url: String,
    cacheDirectory: File,
    offline: Boolean,
): File {
    val key = MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
    val archive = cacheDirectory.resolve(key).resolve(URI(url).path.substringAfterLast('/'))
    if (archive.isFile) return archive
    check(!offline) { "Archive is not cached for offline generation: $url" }
    archive.parentFile.mkdirs()
    val temporary = Files.createTempFile(archive.parentFile.toPath(), "download-", ".part")
    try {
        URI(url)
            .toURL()
            .openConnection()
            .apply {
                connectTimeout = 30_000
                readTimeout = 30_000
            }.getInputStream()
            .use { input -> Files.newOutputStream(temporary).use(input::copyTo) }
        try {
            Files.move(temporary, archive.toPath(), ATOMIC_MOVE)
        } catch (error: FileAlreadyExistsException) {
            if (!archive.isFile) throw error
        }
    } finally {
        Files.deleteIfExists(temporary)
    }
    return archive
}
