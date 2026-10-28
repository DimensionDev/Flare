package dev.dimension.flare.data.translation.image

import dev.dimension.flare.data.datastore.model.ImageTranslationConfig
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiMedia
import org.koin.core.annotation.Single

@Single
internal actual class ImageTranslationRepository {
    actual fun newSession(): String = ""

    actual suspend fun translate(
        media: UiMedia.Image,
        owner: MicroBlogKey?,
        config: ImageTranslationConfig,
        target: String,
        session: String,
        regenerate: Boolean,
    ): UiMedia.Image = error("Image translation is unavailable on Web")

    actual suspend fun closeSession(session: String) {}

    actual suspend fun clear() {}

    actual suspend fun size(): Long = 0

    actual suspend fun removeOwner(owner: MicroBlogKey) {}
}

internal actual fun inspectTranslationImage(path: String): TranslationImageInfo = error("Image translation is unavailable on Web")
