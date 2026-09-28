package dev.dimension.flare.media

import dev.dimension.flare.data.repository.UgoiraRepository
import dev.dimension.flare.di.koinInject
import dev.dimension.flare.ui.model.UiMedia

/** Native UI bridge; dependencies and cache lifetime belong to the repository. */
public actual object UgoiraStore {
    public actual val supportsPlayback: Boolean = true
    private val repository by koinInject<UgoiraRepository>()

    @Throws(Exception::class)
    public actual suspend fun load(
        media: UiMedia.Ugoira,
        onProgress: (Float) -> Unit,
    ): UgoiraAnimation = repository.load(media, onProgress)

    @Throws(Exception::class)
    public actual suspend fun invalidate(animation: UgoiraAnimation): Unit = repository.invalidate(animation)

    public actual fun release(animation: UgoiraAnimation): Unit = repository.release(animation)

    @Throws(Exception::class)
    public actual suspend fun clearCache(): Unit = repository.clear()

    @Throws(Exception::class)
    public actual suspend fun cacheSize(): Long = repository.size()
}
