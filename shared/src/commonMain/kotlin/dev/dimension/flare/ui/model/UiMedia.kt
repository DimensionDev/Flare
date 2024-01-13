package dev.dimension.flare.ui.model

sealed interface UiMedia {
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

    data class Audio(
        val url: String,
        val description: String?,
        val previewUrl: String?,
    ) : UiMedia
}
