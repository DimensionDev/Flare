package dev.dimension.flare.ui.model

sealed interface UiMedia {
    data class Image(
        val url: String,
        val previewUrl: String,
        val description: String?,
        val aspectRatio: Float
    ) : UiMedia

    data class Video(
        val url: String,
        val thumbnailUrl: String,
        val description: String?,
        val aspectRatio: Float
    ) : UiMedia

    data class Gif(
        val url: String,
        val previewUrl: String,
        val description: String?,
        val aspectRatio: Float
    ) : UiMedia

    data class Audio(
        val url: String,
        val description: String?
    ) : UiMedia
}
