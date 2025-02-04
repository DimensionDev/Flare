package dev.dimension.flare.ui.presenter.home.rss

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.paging.compose.collectAsLazyPagingItems
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.toPagingState
import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.datasource.rss.RssDataSource
import dev.dimension.flare.ui.model.UiRssSource
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.mapper.render
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.ui.presenter.home.TimelinePresenter
import dev.dimension.flare.ui.presenter.home.TimelineState
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.getValue

public class RssTimelinePresenter(
    private val url: String,
) : TimelinePresenter() {
    @Composable
    override fun listState(): PagingState<UiTimeline> =
        remember(url) {
            RssDataSource.fetch(url)
        }.collectAsLazyPagingItems().toPagingState()
}

public class RssSourcePresenter(
    private val id: Int,
) : PresenterBase<RssSourcePresenter.State>(),
    KoinComponent {
    private val appDatabase by inject<AppDatabase>()

    public interface State {
        public val data: UiState<UiRssSource>
        public val timelineState: UiState<TimelineState>
    }

    @Composable
    override fun body(): State {
        val data by remember(id) {
            appDatabase
                .rssSourceDao()
                .get(id)
                .map {
                    it.render()
                }
        }.collectAsUiState()
        val timelineState =
            data.map {
                remember(it.url) {
                    RssTimelinePresenter(it.url)
                }.body()
            }
        return object : State {
            override val data = data
            override val timelineState = timelineState
        }
    }
}
