package dev.dimension.flare.ui.presenter.home.rss

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import dev.dimension.flare.data.network.rss.RssService
import dev.dimension.flare.ui.model.UiRssSource
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.flattenUiState
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.mapper.title
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.ui.presenter.home.rss.CheckRssSourcePresenter.State.RssState
import dev.dimension.flare.ui.render.toUi
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Instant

public class CheckRssSourcePresenter(
    private val url: String,
) : PresenterBase<CheckRssSourcePresenter.State>() {
    public interface State {
        public val state: UiState<RssState>

        public sealed interface RssState {
            public data object RssHub : RssState

            public data class RssFeed(
                val title: String,
                val url: String,
            ) : RssState

            public data class RssSources(
                val sources: ImmutableList<UiRssSource>,
            ) : RssState
        }
    }

    private val flow: Flow<UiState<RssState>> by lazy {
        flow {
            emit(UiState.Loading())
            runCatching {
                if (url.startsWith("rsshub://", ignoreCase = true)) {
                    State.RssState.RssHub
                } else if (url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true)) {
                    // Valid URL, proceed to fetch
                    val feed =
                        runCatching {
                            RssService.fetch(url)
                        }
                    if (feed.isSuccess) {
                        RssState.RssFeed(feed.getOrThrow().title.trim(), url)
                    } else {
                        coroutineScope {
                            RssService
                                .detectLinkSources(url)
                                .map {
                                    async {
                                        UiRssSource(
                                            id = 0, // ID will be set later when saving to the database
                                            url = it,
                                            title = RssService.fetch(it).title.trim(),
                                            lastUpdate = Instant.DISTANT_PAST.toUi(), // Last update will be set later
                                        )
                                    }
                                }.awaitAll()
                                .toImmutableList()
                                .let {
                                    RssState.RssSources(it)
                                }
                        }
                    }
                } else {
                    // Invalid URL format
                    throw IllegalArgumentException("Invalid URL format: $url")
                }
            }.onFailure {
                emit(UiState.Error(it))
            }.onSuccess {
                emit(UiState.Success(it))
            }
        }
    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    @Composable
    override fun body(): State {
        val state by flow.flattenUiState()
        return object : State {
            override val state: UiState<RssState>
                get() = state
        }
    }
}
