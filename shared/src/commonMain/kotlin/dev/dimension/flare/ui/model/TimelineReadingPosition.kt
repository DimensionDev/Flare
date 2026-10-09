package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable

@Immutable
public data class TimelineReadingPosition(
    public val itemKey: String,
    public val offset: Double,
    public val requestId: Long,
    public val latest: Boolean = false,
)

@Immutable
public interface TimelineReadingState {
    public val position: TimelineReadingPosition?
    public val hasNewContent: Boolean
    public val isRefreshing: Boolean

    public fun updateViewport(
        itemKey: String?,
        offset: Double,
        atTop: Boolean,
        interacting: Boolean,
    )

    public fun positionRestored(requestId: Long)

    public fun savePosition()

    public fun cancelRestoration()

    public suspend fun refreshAutomatically()

    public suspend fun showLatest()
}
