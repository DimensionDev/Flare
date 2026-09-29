package dev.dimension.flare.data.repository

import dev.dimension.flare.media.UgoiraAnimation
import dev.dimension.flare.ui.model.UiMedia

internal expect class UgoiraRepository {
    suspend fun load(
        media: UiMedia.Ugoira,
        onProgress: (Float) -> Unit,
    ): UgoiraAnimation

    suspend fun invalidate(animation: UgoiraAnimation)

    fun release(animation: UgoiraAnimation)

    suspend fun clear()

    suspend fun size(): Long
}
