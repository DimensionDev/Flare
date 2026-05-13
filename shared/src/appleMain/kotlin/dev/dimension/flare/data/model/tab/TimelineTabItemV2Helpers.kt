package dev.dimension.flare.data.model.tab

public object TimelineTabItemV2Helpers {
    public fun isSystemHomeMixedTimeline(item: TimelineTabItemV2): Boolean = item.isSystemHomeMixedTimeline

    public fun withSystemHomeMixedTimelineEnabled(
        tabs: List<TimelineTabItemV2>,
        enabled: Boolean,
        mergePolicy: TimelineMergePolicy? = null,
    ): List<TimelineTabItemV2> =
        tabs.withSystemHomeMixedTimelineEnabled(
            enabled = enabled,
            mergePolicy = mergePolicy,
        )
}
