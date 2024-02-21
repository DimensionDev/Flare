package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable

@Immutable
sealed interface UiMedia {
    @Immutable
    data class Image(
        val url: String,
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
        val url: String,
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
        val url: String,
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
        val url: String,
        val description: String?,
        val previewUrl: String?,
    ) : UiMedia
}
