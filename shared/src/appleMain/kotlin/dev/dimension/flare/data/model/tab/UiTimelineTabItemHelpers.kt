package dev.dimension.flare.data.model.tab

public object UiTimelineTabItemHelpers {
    public fun isSystemHomeMixedTimeline(item: UiTimelineTabItem): Boolean = item.isSystemHomeMixedTimeline

    public fun withSystemHomeMixedTimelineEnabled(
        tabs: List<UiTimelineTabItem>,
        enabled: Boolean,
        mergePolicy: TimelineMergePolicy? = null,
    ): List<UiTimelineTabItem> =
        tabs.withSystemHomeMixedTimelineEnabled(
            enabled = enabled,
            mergePolicy = mergePolicy,
        )
}
