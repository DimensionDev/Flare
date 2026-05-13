package dev.dimension.flare.data.datasource.microblog.paging

import dev.dimension.flare.ui.model.UiTimelineV2

internal interface SortIdProvider {
    suspend fun sortId(data: UiTimelineV2): Long?
}
