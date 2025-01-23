package dev.dimension.flare.ui.presenter.home.rss

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.paging.compose.collectAsLazyPagingItems
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.toPagingState
import dev.dimension.flare.data.datasource.rss.RssDataSource
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.presenter.home.TimelinePresenter

public class RssTimelinePresenter(
    private val url: String,
) : TimelinePresenter() {
    @Composable
    override fun listState(): PagingState<UiTimeline> =
        remember(url) {
            RssDataSource.fetch(url)
        }.collectAsLazyPagingItems().toPagingState()
}
