package dev.dimension.flare.ui.presenter.home.rss

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.dimension.flare.data.database.app.model.SubscriptionType
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.datasource.rss.RssDataSource
import dev.dimension.flare.data.repository.SubscriptionRepository
import dev.dimension.flare.data.subscription.SubscriptionTimelineLoaderFactory
import dev.dimension.flare.ui.model.UiRssSource
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.map
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
    override val loader: Flow<RemoteLoader<UiTimelineV2>> by lazy {
        flowOf(RssDataSource.fetchLoader(url))
    }
}

public class SubscriptionTimelinePresenter(
    private val type: SubscriptionType,
    private val url: String,
) : TimelinePresenter() {
    override val loader: Flow<RemoteLoader<UiTimelineV2>> by lazy {
        flowOf(RssDataSource.fetchLoader(type, url))
    }
}

public class AllRssTimelinePresenter :
    TimelinePresenter(),
    KoinComponent {
    private val subscriptionRepository: SubscriptionRepository by inject()
    private val subscriptionTimelineLoaderFactory: SubscriptionTimelineLoaderFactory by inject()

    override val loader: Flow<RemoteLoader<UiTimelineV2>> by lazy {
        subscriptionRepository
            .observeAll()
            .map { items ->
                subscriptionTimelineLoaderFactory.mixedTimeline(
                    loaders =
                        items.map {
                            RssDataSource.fetchLoader(it)
                        },
                )
            }
    }
}

public class RssSourcePresenter(
    private val id: Int,
) : PresenterBase<RssSourcePresenter.State>(),
    KoinComponent {
    private val subscriptionRepository: SubscriptionRepository by inject()

    @androidx.compose.runtime.Immutable
    public interface State {
        public val data: UiState<UiRssSource>
        public val timelineState: UiState<TimelineState>
    }

    @Composable
    override fun body(): State {
        val data by remember(id) {
            subscriptionRepository.observe(id)
        }.collectAsUiState()
        val timelineState =
            data.map {
                remember(it.url, it.type) {
                    SubscriptionTimelinePresenter(it.type, it.url)
                }.body()
            }
        return object : State {
            override val data = data
            override val timelineState = timelineState
        }
    }
}
