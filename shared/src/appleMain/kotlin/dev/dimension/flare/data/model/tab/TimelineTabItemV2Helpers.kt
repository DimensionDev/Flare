package dev.dimension.flare.data.model.tab

import dev.dimension.flare.model.AccountType

public object TimelineTabItemV2Helpers {
    public fun isSystemHomeMixedTimeline(item: TimelineTabItemV2): Boolean = item.isSystemHomeMixedTimeline

    public fun xqtDeviceFollow(accountType: AccountType): SourceTimelineTabItemV2? {
        val accountKey = (accountType as? AccountType.Specific)?.accountKey ?: return null
        return SourceTimelineTabItemV2.xqtDeviceFollow(accountKey)
    }

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
