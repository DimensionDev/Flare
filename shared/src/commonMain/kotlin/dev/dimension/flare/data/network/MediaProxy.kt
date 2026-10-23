package dev.dimension.flare.data.network

import dev.dimension.flare.ui.model.UiMedia

public data class MediaPlaybackSource(
    val url: String,
    val key: String,
    val originalUrl: String = url,
    val snapshot: ProxySnapshot? = null,
)

// Only native players without a per-request network adapter use this loopback relay.
public expect object MediaProxy {
    @Throws(Exception::class)
    public suspend fun playbackSource(media: UiMedia): MediaPlaybackSource

    public fun retain(source: MediaPlaybackSource)

    public fun release(source: MediaPlaybackSource)

    public fun failureMessage(localUrl: String): String?
}
