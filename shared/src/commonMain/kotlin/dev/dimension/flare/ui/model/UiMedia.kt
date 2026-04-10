package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable
import dev.dimension.flare.common.SerializableImmutableMap
import dev.dimension.flare.common.sanitizeFileName
import kotlinx.serialization.Serializable

@Serializable
@Immutable
public sealed interface UiMedia {
    public val url: String
    public val description: String?
    public val customHeaders: SerializableImmutableMap<String, String>?

    @Serializable
    @Immutable
    public data class Image internal constructor(
        override val url: String,
        val previewUrl: String,
        override val description: String?,
        val height: Float,
        val width: Float,
        val sensitive: Boolean,
        override val customHeaders: SerializableImmutableMap<String, String>? = null,
    ) : UiMedia {
        internal constructor(url: String, customHeaders: SerializableImmutableMap<String, String>? = null) : this(
            url = url,
            previewUrl = url,
            description = null,
            height = 0f,
            width = 0f,
            sensitive = false,
            customHeaders = customHeaders,
        )

        val aspectRatio: Float
            get() = (width / (height.takeUnless { it == 0f } ?: 1f)).takeUnless { it == 0f } ?: 1f
    }

    @Serializable
    @Immutable
    public data class Video internal constructor(
        override val url: String,
        val thumbnailUrl: String,
        override val description: String?,
        val height: Float,
        val width: Float,
        override val customHeaders: SerializableImmutableMap<String, String>? = null,
    ) : UiMedia {
        val aspectRatio: Float
            get() = (width / (height.takeUnless { it == 0f } ?: 1f)).takeUnless { it == 0f } ?: 1f
    }

    @Serializable
    @Immutable
    public data class Gif internal constructor(
        override val url: String,
        val previewUrl: String,
        override val description: String?,
        val height: Float,
        val width: Float,
        override val customHeaders: SerializableImmutableMap<String, String>? = null,
    ) : UiMedia {
        val aspectRatio: Float
            get() = (width / (height.takeUnless { it == 0f } ?: 1f)).takeUnless { it == 0f } ?: 1f
    }

    @Serializable
    @Immutable
    public data class Audio internal constructor(
        override val url: String,
        override val description: String?,
        val previewUrl: String?,
        override val customHeaders: SerializableImmutableMap<String, String>? = null,
    ) : UiMedia
}

public fun UiMedia.getFileName(
    statusKey: String,
    userHandle: String,
): String {
    val key = statusKey.sanitizeFileName()
    val handle = userHandle.sanitizeFileName()
    val path = url.substringBefore("?").substringBefore("#")
    val originalName = path.substringAfterLast("/")
    val lastDotIndex = originalName.lastIndexOf('.')
    val lastAtIndex = originalName.lastIndexOf('@')
    val separatorIndex = maxOf(lastDotIndex, lastAtIndex)
    val extension =
        if (separatorIndex >= 0 && separatorIndex < originalName.length - 1) {
            originalName.substring(separatorIndex + 1)
        } else {
            when (this) {
                is UiMedia.Audio -> "mp3"
                is UiMedia.Gif -> "gif"
                is UiMedia.Image -> "jpg"
                is UiMedia.Video -> "mp4"
            }
        }
    return "${key}_$handle.$extension"
}

private fun String.sanitizeFileName(): String = replace(Regex("[^A-Za-z0-9._-]"), "_")
