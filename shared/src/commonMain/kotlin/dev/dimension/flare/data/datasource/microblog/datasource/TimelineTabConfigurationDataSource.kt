package dev.dimension.flare.data.datasource.microblog.datasource

import dev.dimension.flare.data.model.tab.ShortcutSpec
import dev.dimension.flare.data.model.tab.TimelineTabItemV2
import kotlinx.collections.immutable.ImmutableList

public interface TimelineTabConfigurationDataSource {
    public val defaultTabs: ImmutableList<TimelineTabItemV2>
    public val builtInTimelineTabs: ImmutableList<TimelineTabItemV2>
    public val shortcuts: ImmutableList<ShortcutSpec>
}
