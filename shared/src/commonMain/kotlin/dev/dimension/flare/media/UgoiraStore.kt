package dev.dimension.flare.media

import dev.dimension.flare.ui.model.UiMedia

public expect object UgoiraStore {
    public val supportsPlayback: Boolean

    @Throws(Exception::class)
    public suspend fun load(
        media: UiMedia.Ugoira,
        onProgress: (Float) -> Unit,
    ): UgoiraAnimation

    @Throws(Exception::class)
    public suspend fun invalidate(animation: UgoiraAnimation)

    public fun release(animation: UgoiraAnimation)

    @Throws(Exception::class)
    public suspend fun clearCache()

    @Throws(Exception::class)
    public suspend fun cacheSize(): Long
}
