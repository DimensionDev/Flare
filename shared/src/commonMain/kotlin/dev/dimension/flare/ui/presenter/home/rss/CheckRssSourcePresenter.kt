package dev.dimension.flare.ui.presenter.home.rss

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import dev.dimension.flare.data.network.rss.RssService
import dev.dimension.flare.ui.model.UiRssSource
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.flattenUiState
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
                val icon: String?,
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
                } else {
                    val actualUrl =
                        if (url.startsWith("http://", ignoreCase = true)) {
                            url.replaceFirst("http://", "https://", ignoreCase = true)
                        } else if (!url.startsWith("https://", ignoreCase = true)) {
                            "https://$url"
                        } else {
                            url
                        }
                    val feed =
                        runCatching {
                            RssService.fetch(actualUrl)
                        }
                    if (feed.isSuccess) {
                        val icon =
                            runCatching {
                                RssService.fetchIcon(actualUrl)
                            }.getOrNull()
                        RssState.RssFeed(
                            feed.getOrThrow().title.trim(),
                            actualUrl,
                            icon,
                        )
                    } else {
                        coroutineScope {
                            RssService
                                .detectLinkSources(actualUrl)
                                .map {
                                    async {
                                        val icon =
                                            runCatching {
                                                RssService.fetchIcon(it)
                                            }.getOrNull()
                                        UiRssSource(
                                            id = 0, // ID will be set later when saving to the database
                                            url = it,
                                            title = RssService.fetch(it).title.trim(),
                                            lastUpdate = Instant.DISTANT_PAST.toUi(), // Last update will be set later
                                            favIcon = icon,
                                            openInBrowser = false,
                                        )
                                    }
                                }.awaitAll()
                                .toImmutableList()
                                .let {
                                    RssState.RssSources(it)
                                }
                        }
                    }
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
