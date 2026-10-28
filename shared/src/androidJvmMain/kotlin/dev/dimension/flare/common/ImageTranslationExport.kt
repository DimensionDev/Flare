package dev.dimension.flare.common

import dev.dimension.flare.ui.model.UiMedia
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.util.UUID

public fun isImageTranslationFile(
    media: UiMedia,
    cacheDirectory: File,
): Boolean {
    if (!media.url.startsWith("file://")) return false
    val file = runCatching { File(URI(media.url)).canonicalFile }.getOrNull() ?: return false
    return listOf("image_translation_sessions", "translated_exports").any {
        file.toPath().startsWith(File(cacheDirectory, it).canonicalFile.toPath())
    }
}

/** Pin an owned translation before a save dialog or asynchronous share can outlive its viewer. */
public fun prepareImageTranslationExport(
    media: UiMedia,
    cacheDirectory: File,
): UiMedia {
    if (media !is UiMedia.Image || !media.url.startsWith("file://")) return media
    if (!isImageTranslationFile(media, cacheDirectory)) return media
    return runCatching {
        val source = File(URI(media.url))
        val root = File(cacheDirectory, "translated_exports").apply { mkdirs() }
        root
            .listFiles()
            .orEmpty()
            .filter {
                System.currentTimeMillis() - it.lastModified() > 24L * 60 * 60 * 1000
            }.forEach { it.deleteRecursively() }
        val directory = File(root, UUID.randomUUID().toString()).apply { mkdirs() }
        val file = File(directory, source.name)
        try {
            try {
                Files.createLink(file.toPath(), source.toPath())
            } catch (_: Exception) {
                source.copyTo(file, overwrite = true)
            }
        } catch (error: Exception) {
            directory.deleteRecursively()
            throw error
        }
        media.copy(url = file.toPath().toUri().toString(), previewUrl = file.toPath().toUri().toString())
    }.getOrDefault(media)
}
