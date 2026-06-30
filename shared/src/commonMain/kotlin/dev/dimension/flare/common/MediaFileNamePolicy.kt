package dev.dimension.flare.common

import dev.dimension.flare.ui.model.UiMedia
import kotlin.time.Clock

public object MediaFileNamePolicy {
    public fun statusMediaFileName(
        statusKey: String,
        userHandle: String,
        media: UiMedia,
    ): String {
        val key = sanitizeFileName(statusKey)
        val handle = sanitizeFileName(userHandle)
        val extension = extensionFromUrl(url = media.url, fallbackExtension = media.fallbackExtension)
        return "${key}_$handle.$extension"
    }

    public fun statusMediaFileNames(
        statusKey: String,
        userHandle: String,
        medias: List<UiMedia>,
    ): Map<String, UiMedia> {
        val key = sanitizeFileName(statusKey)
        val handle = sanitizeFileName(userHandle)
        return indexedMediaFileNames(medias) { media ->
            val extension = extensionFromUrl(url = media.url, fallbackExtension = media.fallbackExtension)
            "${key}_${handle}.$extension"
        }
    }

    public fun rawMediaFileName(media: UiMedia): String {
        val path = media.url.cleanUrlPath()
        var fileName = path.substringAfterLast("/").substringAfterLast("\\")
        val lastAtIndex = fileName.lastIndexOf('@')
        val lastDotIndex = fileName.lastIndexOf('.')
        if (lastAtIndex > lastDotIndex && lastAtIndex < fileName.length - 1) {
            fileName = fileName.substring(0, lastAtIndex) + "." + fileName.substring(lastAtIndex + 1)
        }
        fileName = sanitizeFileName(fileName.ifBlank { "media" }, fallback = "media")
        return if (fileName.contains(".")) {
            fileName
        } else {
            "$fileName.${media.fallbackExtension}"
        }
    }

    public fun rawMediaFileNames(medias: List<UiMedia>): Map<String, UiMedia> =
        indexedMediaFileNames(medias, ::rawMediaFileName)

    public fun articleFileName(
        name: String?,
        url: String,
        extensionName: String?,
    ): String {
        val sourceName =
            name
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: fileNameFromUrl(url)
                ?: "file"
        val normalizedExtension =
            extensionName
                ?.trim()
                ?.trimStart('.')
                ?.takeIf { it.isNotBlank() }
        val fileName =
            if (normalizedExtension != null && !sourceName.hasFileExtension()) {
                "$sourceName.$normalizedExtension"
            } else {
                sourceName
            }
        return safeDownloadFileName(fileName)
    }

    public fun screenshotFileName(statusKey: String): String {
        val timestampMillis = Clock.System.now().toEpochMilliseconds()
        return "status_${sanitizeFileName(statusKey)}_$timestampMillis.png"
    }

    public fun sanitizeFileName(
        value: String?,
        fallback: String = "file",
    ): String {
        val safeName = value.orEmpty().replace(UnsafeFileNameCharacterRegex, "_")
        return safeName.ifBlank {
            fallback
                .replace(UnsafeFileNameCharacterRegex, "_")
                .ifBlank { fallback }
        }
    }

    public fun safeDownloadFileName(
        value: String?,
        fallback: String = "file",
    ): String {
        val safeName = value.orEmpty().trim().replaceUnsafeDownloadFileNameCharacters()
        return safeName.ifBlank {
            fallback
                .trim()
                .replaceUnsafeDownloadFileNameCharacters()
                .ifBlank { "file" }
        }
    }

    public fun safeLocalFileName(
        value: String?,
        fallback: String = "file",
    ): String {
        val safeName = value.orEmpty().trim().replaceUnsafeLocalFileNameCharacters()
        return safeName.ifBlank {
            fallback
                .trim()
                .replaceUnsafeLocalFileNameCharacters()
                .ifBlank { "file" }
        }
    }

    public fun extensionFromUrl(
        url: String,
        fallbackExtension: String,
    ): String =
        extensionFromName(url.cleanUrlPath().substringAfterLast("/").substringAfterLast("\\"))
            ?: fallbackExtension.trim().trimStart('.').ifBlank { "bin" }

    private fun fileNameFromUrl(url: String): String? =
        url
            .cleanUrlPath()
            .substringAfterLast("/")
            .substringAfterLast("\\")
            .trim()
            .takeIf { it.isNotBlank() }

    private fun extensionFromName(name: String): String? {
        val lastDotIndex = name.lastIndexOf('.')
        val lastAtIndex = name.lastIndexOf('@')
        val separatorIndex = maxOf(lastDotIndex, lastAtIndex)
        return name
            .takeIf { separatorIndex >= 0 && separatorIndex < name.length - 1 }
            ?.substring(separatorIndex + 1)
    }

    private fun String.cleanUrlPath(): String = substringBefore("?").substringBefore("#")

    private fun String.hasFileExtension(): Boolean {
        val name = substringAfterLast("/").substringAfterLast("\\")
        val lastDotIndex = name.lastIndexOf('.')
        return lastDotIndex > 0 && lastDotIndex < name.length - 1
    }

    private fun indexedMediaFileNames(
        medias: List<UiMedia>,
        fileName: (UiMedia) -> String,
    ): Map<String, UiMedia> {
        val indexWidth = medias.size.toString().length.coerceAtLeast(2)
        return medias
            .mapIndexed { index, media ->
                fileName(media).withIndexSuffix(
                    index = index + 1,
                    width = indexWidth,
                ) to media
            }.toMap()
    }

    private fun String.withIndexSuffix(
        index: Int,
        width: Int,
    ): String {
        val suffix = index.toString().padStart(width, '0')
        val extensionIndex = lastIndexOf('.')
        return if (extensionIndex > 0 && extensionIndex < length - 1) {
            "${substring(0, extensionIndex)}_$suffix${substring(extensionIndex)}"
        } else {
            "${this}_$suffix"
        }
    }

    private fun String.replaceUnsafeDownloadFileNameCharacters(): String =
        map { char ->
            if (char == '/' || char == '\\' || char.code < 32 || char.code == 127) {
                '_'
            } else {
                char
            }
        }.joinToString(separator = "")

    private fun String.replaceUnsafeLocalFileNameCharacters(): String =
        map { char ->
            if (char in UnsafeLocalFileNameCharacters || char.code < 32 || char.code == 127) {
                '_'
            } else {
                char
            }
        }.joinToString(separator = "")
}

private val UiMedia.fallbackExtension: String
    get() =
        when (this) {
            is UiMedia.Audio -> "mp3"
            is UiMedia.Gif -> "gif"
            is UiMedia.Image -> "jpg"
            is UiMedia.Video -> "mp4"
        }

private val UnsafeFileNameCharacterRegex = Regex("[^A-Za-z0-9._-]")

private val UnsafeLocalFileNameCharacters = setOf('/', '\\', '?', '%', '*', '|', '"', '<', '>', ':')
