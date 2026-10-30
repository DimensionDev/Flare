package dev.dimension.flare.data.datasource.microblog.datasource

import androidx.paging.PagingData
import dev.dimension.flare.data.model.tab.TimelineCandidate
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.UiText
import dev.dimension.flare.ui.model.asText
import kotlinx.coroutines.flow.Flow
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public interface PinnableTimelineTabDataSource {
    public val pinnableTimelineTabs: List<PinnableTimelineTabSection>
}

@HiddenFromObjC
public data class PinnableTimelineTabSection(
    val title: UiText,
    val data: Flow<PagingData<TimelineCandidate<*>>>,
) {
    public constructor(
        title: UiStrings,
        data: Flow<PagingData<TimelineCandidate<*>>>,
    ) : this(title = title.asText(), data = data)
}
