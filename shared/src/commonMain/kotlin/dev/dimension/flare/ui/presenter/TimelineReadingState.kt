package dev.dimension.flare.ui.presenter

import dev.dimension.flare.ui.model.TimelineReadingPosition

public interface TimelineReadingState {
    public val restorePosition: TimelineReadingPosition?
    public val hasNewContent: Boolean

    public fun viewportChanged(
        itemKey: String,
        offset: Double,
    )

    public fun positionChanged(
        itemKey: String,
        offset: Double,
    )

    public fun restorationFinished()

    /** Reapply this reader's bookmark when its native list is recreated. */
    public fun restoreCurrentPosition(): Unit = Unit

    public fun showLatest()
}
