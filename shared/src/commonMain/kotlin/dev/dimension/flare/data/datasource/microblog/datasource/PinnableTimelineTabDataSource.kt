package dev.dimension.flare.data.datasource.microblog.datasource

import androidx.paging.PagingData
import dev.dimension.flare.data.model.tab.TimelineTabItemV2
import dev.dimension.flare.ui.model.UiStrings
import kotlinx.coroutines.flow.Flow

public interface PinnableTimelineTabDataSource {
    public val pinnableTimelineTabs: List<PinnableTimelineTabSection>
}

public data class PinnableTimelineTabSection(
    val title: UiStrings,
    val data: Flow<PagingData<TimelineTabItemV2>>,
)
