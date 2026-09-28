package dev.dimension.flare.media

import dev.dimension.flare.ui.model.UiMedia

public actual object UgoiraStore {
    public actual val supportsPlayback: Boolean = false

    public actual suspend fun load(
        media: UiMedia.Ugoira,
        onProgress: (Float) -> Unit,
    ): UgoiraAnimation = error("Pixiv playback is not available on the web")

    public actual suspend fun invalidate(animation: UgoiraAnimation): Unit = Unit

    public actual fun release(animation: UgoiraAnimation) {}

    public actual suspend fun clearCache() {}

    public actual suspend fun cacheSize(): Long = 0
}
