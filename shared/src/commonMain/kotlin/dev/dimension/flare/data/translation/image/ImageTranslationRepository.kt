package dev.dimension.flare.data.translation.image

import dev.dimension.flare.data.datastore.model.ImageTranslationConfig
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiMedia
import kotlinx.serialization.Serializable

internal expect class ImageTranslationRepository {
    fun newSession(): String

    suspend fun translate(
        media: UiMedia.Image,
        owner: MicroBlogKey?,
        config: ImageTranslationConfig,
        target: String,
        session: String,
        regenerate: Boolean,
    ): UiMedia.Image

    suspend fun closeSession(session: String)

    suspend fun clear()

    suspend fun size(): Long

    suspend fun removeOwner(owner: MicroBlogKey)
}

@Serializable
internal data class TranslationImageInfo(
    val width: Int,
    val height: Int,
    val mimeType: String,
) {
    val extension: String get() =
        when (mimeType) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> throw ImageTranslationException(ImageTranslationError.InvalidImage)
        }
}

internal expect fun inspectTranslationImage(path: String): TranslationImageInfo
