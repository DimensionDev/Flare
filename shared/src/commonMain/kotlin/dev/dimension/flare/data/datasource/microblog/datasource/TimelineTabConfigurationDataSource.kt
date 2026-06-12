package dev.dimension.flare.data.datasource.microblog.datasource

import dev.dimension.flare.data.model.tab.ShortcutSpec
import dev.dimension.flare.data.model.tab.TimelineCandidate
import kotlinx.collections.immutable.ImmutableList
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public interface TimelineTabConfigurationDataSource {
    public val defaultTabs: ImmutableList<TimelineCandidate<*>>
    public val builtInTimelineTabs: ImmutableList<TimelineCandidate<*>>
    public val shortcuts: ImmutableList<ShortcutSpec>
}
