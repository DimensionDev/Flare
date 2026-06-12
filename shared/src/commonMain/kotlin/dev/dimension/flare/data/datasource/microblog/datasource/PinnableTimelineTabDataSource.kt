package dev.dimension.flare.data.datasource.microblog.datasource

import androidx.paging.PagingData
import dev.dimension.flare.data.model.tab.TimelineCandidate
import dev.dimension.flare.ui.model.UiStrings
import kotlinx.coroutines.flow.Flow
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public interface PinnableTimelineTabDataSource {
    public val pinnableTimelineTabs: List<PinnableTimelineTabSection>
}

@HiddenFromObjC
public data class PinnableTimelineTabSection(
    val title: UiStrings,
    val data: Flow<PagingData<TimelineCandidate<*>>>,
)
