package dev.dimension.flare.data.repository

import dev.dimension.flare.media.UgoiraAnimation
import dev.dimension.flare.ui.model.UiMedia
import kotlinx.coroutines.Job
import org.koin.core.annotation.Single

@Single
internal actual class UgoiraRepository {
    actual suspend fun load(
        media: UiMedia.Ugoira,
        onProgress: (Float) -> Unit,
    ): UgoiraAnimation = error("Pixiv playback is not available on the web")

    actual suspend fun invalidate(animation: UgoiraAnimation): Unit = Unit

    actual fun release(animation: UgoiraAnimation): Job = Job().apply { complete() }

    actual suspend fun clear() {}

    actual suspend fun size(): Long = 0
}
