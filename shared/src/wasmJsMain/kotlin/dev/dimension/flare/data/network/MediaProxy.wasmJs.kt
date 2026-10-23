package dev.dimension.flare.data.network

import dev.dimension.flare.ui.model.UiMedia

public actual object MediaProxy {
    public actual suspend fun playbackSource(media: UiMedia): MediaPlaybackSource = MediaPlaybackSource(media.url, media.cacheKey)

    public actual fun retain(source: MediaPlaybackSource): Unit = Unit

    public actual fun release(source: MediaPlaybackSource): Unit = Unit

    public actual fun failureMessage(localUrl: String): String? = null
}
