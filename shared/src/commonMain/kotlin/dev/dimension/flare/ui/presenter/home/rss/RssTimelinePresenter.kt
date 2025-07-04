package dev.dimension.flare.ui.presenter.home.rss

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.dimension.flare.common.BaseTimelineLoader
import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.datasource.rss.RssDataSource
import dev.dimension.flare.ui.model.UiRssSource
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.mapper.render
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.ui.presenter.home.TimelinePresenter
import dev.dimension.flare.ui.presenter.home.TimelineState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class RssTimelinePresenter(
    private val url: String,
) : TimelinePresenter() {
    override val loader: Flow<BaseTimelineLoader> by lazy {
        flowOf(RssDataSource.fetchLoader(url))
    }
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
