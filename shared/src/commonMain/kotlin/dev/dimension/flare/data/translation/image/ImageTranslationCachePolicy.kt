package dev.dimension.flare.data.translation.image

import kotlinx.coroutines.flow.MutableStateFlow

public object ImageTranslationCachePolicy {
    private val ordinarySeconds = MutableStateFlow(14.0 * 24 * 60 * 60)

    public fun configure(ordinaryRetentionSeconds: Double) {
        ordinarySeconds.value = ordinaryRetentionSeconds.takeIf { it.isFinite() && it > 0 } ?: (14.0 * 24 * 60 * 60)
    }

    internal val retentionMillis: Long get() = (ordinarySeconds.value * 2000).toLong()
}
