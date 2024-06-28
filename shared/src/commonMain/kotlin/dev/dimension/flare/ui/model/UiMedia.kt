package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable

@Immutable
sealed interface UiMedia {
    val url: String

    @Immutable
    data class Image(
        override val url: String,
        val previewUrl: String,
        val description: String?,
        val height: Float,
        val width: Float,
        val sensitive: Boolean,
    ) : UiMedia {
        val aspectRatio: Float
            get() = (width / (height.takeUnless { it == 0f } ?: 1f)).takeUnless { it == 0f } ?: 1f
    }

    @Immutable
    data class Video(
        override val url: String,
        val thumbnailUrl: String,
        val description: String?,
        val height: Float,
        val width: Float,
    ) : UiMedia {
        val aspectRatio: Float
            get() = (width / (height.takeUnless { it == 0f } ?: 1f)).takeUnless { it == 0f } ?: 1f
    }

    @Immutable
    data class Gif(
        override val url: String,
        val previewUrl: String,
        val description: String?,
        val height: Float,
        val width: Float,
    ) : UiMedia {
        val aspectRatio: Float
            get() = (width / (height.takeUnless { it == 0f } ?: 1f)).takeUnless { it == 0f } ?: 1f
    }

    @Immutable
    data class Audio(
        override val url: String,
        val description: String?,
        val previewUrl: String?,
    ) : UiMedia
}
