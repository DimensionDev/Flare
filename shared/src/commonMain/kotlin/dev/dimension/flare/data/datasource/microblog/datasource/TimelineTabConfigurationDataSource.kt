package dev.dimension.flare.data.datasource.microblog.datasource

import dev.dimension.flare.data.model.tab.ShortcutSpec
import dev.dimension.flare.data.model.tab.TimelineTabItemV2
import kotlinx.collections.immutable.ImmutableList

internal interface TimelineTabConfigurationDataSource {
    val defaultTabs: ImmutableList<TimelineTabItemV2>
    val builtInTimelineTabs: ImmutableList<TimelineTabItemV2>
    val shortcuts: ImmutableList<ShortcutSpec>
}
