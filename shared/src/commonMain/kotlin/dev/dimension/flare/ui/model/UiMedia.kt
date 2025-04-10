package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableMap

@Immutable
public sealed interface UiMedia {
    public val url: String
    public val description: String?
    public val customHeaders: ImmutableMap<String, String>?

    @Immutable
    public data class Image internal constructor(
        override val url: String,
        val previewUrl: String,
        override val description: String?,
        val height: Float,
        val width: Float,
        val sensitive: Boolean,
        override val customHeaders: ImmutableMap<String, String>? = null,
    ) : UiMedia {
        val aspectRatio: Float
            get() = (width / (height.takeUnless { it == 0f } ?: 1f)).takeUnless { it == 0f } ?: 1f
    }

    @Immutable
    public data class Video internal constructor(
        override val url: String,
        val thumbnailUrl: String,
        override val description: String?,
        val height: Float,
        val width: Float,
        override val customHeaders: ImmutableMap<String, String>? = null,
    ) : UiMedia {
        val aspectRatio: Float
            get() = (width / (height.takeUnless { it == 0f } ?: 1f)).takeUnless { it == 0f } ?: 1f
    }

    @Immutable
    public data class Gif internal constructor(
        override val url: String,
        val previewUrl: String,
        override val description: String?,
        val height: Float,
        val width: Float,
        override val customHeaders: ImmutableMap<String, String>? = null,
    ) : UiMedia {
        val aspectRatio: Float
            get() = (width / (height.takeUnless { it == 0f } ?: 1f)).takeUnless { it == 0f } ?: 1f
    }

    @Immutable
    public data class Audio internal constructor(
        override val url: String,
        override val description: String?,
        val previewUrl: String?,
        override val customHeaders: ImmutableMap<String, String>? = null,
    ) : UiMedia
}
