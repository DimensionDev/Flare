package dev.dimension.flare.ui.presenter.home.rss

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import dev.dimension.flare.common.Locale
import dev.dimension.flare.data.database.app.model.RssDisplayMode
import dev.dimension.flare.data.database.app.model.SubscriptionType
import dev.dimension.flare.data.network.FaviconService
import dev.dimension.flare.data.network.rss.RssService
import dev.dimension.flare.data.network.rss.model.Feed
import dev.dimension.flare.data.network.rss.toRssWebUrl
import dev.dimension.flare.di.koinInject
import dev.dimension.flare.model.PlatformRegistry
import dev.dimension.flare.ui.model.UiRssSource
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.flattenUiState
import dev.dimension.flare.ui.model.mapper.title
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.ui.presenter.home.rss.CheckRssSourcePresenter.State.RssState
import dev.dimension.flare.ui.render.toUi
import io.ktor.http.Url
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Instant

public class CheckRssSourcePresenter internal constructor(
    private val url: String,
    private val fetchFeed: suspend (String) -> Feed,
    private val fetchIcon: suspend (String) -> String?,
    private val detectLinkSources: suspend (String) -> List<String>,
) : PresenterBase<CheckRssSourcePresenter.State>() {
    public constructor(url: String) : this(url, RssService::fetch, RssService::fetchIcon, RssService::detectLinkSources)

    internal val feeds: MutableMap<String, Feed> = mutableMapOf()
    private val platformRegistry: PlatformRegistry by koinInject()

    @Immutable
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

            public data class SubscriptionInstance(
                val host: String,
                val instanceName: String?,
                val icon: String?,
                val availableTimelines: ImmutableList<SubscriptionType>,
            ) : RssState
        }
    }

    private val flow: Flow<UiState<RssState>> by lazy {
        flow {
            emit(UiState.Loading())
            delay(666)
            if (url.isBlank()) return@flow
            rssCheck {
                if (url.startsWith("rsshub://", ignoreCase = true)) {
                    State.RssState.RssHub
                } else {
                    val actualUrl = url.toRssWebUrl()
                    val feed =
                        rssCheck {
                            fetchFeed(actualUrl).also { feeds[actualUrl] = it }
                        }
                    if (feed.isSuccess) {
                        val icon =
                            rssCheck {
                                fetchIcon(actualUrl)
                            }.getOrNull()
                        RssState.RssFeed(
                            feed.getOrThrow().title.trim(),
                            actualUrl,
                            icon,
                        )
                    } else {
                        val linkSources =
                            rssCheck {
                                coroutineScope {
                                    detectLinkSources(actualUrl)
                                        .map {
                                            async {
                                                val icon =
                                                    rssCheck {
                                                        fetchIcon(it)
                                                    }.getOrNull()
                                                UiRssSource(
                                                    id = 0,
                                                    url = it,
                                                    title = fetchFeed(it).also { feed -> feeds[it] = feed }.title.trim(),
                                                    lastUpdate = Instant.DISTANT_PAST.toUi(),
                                                    favIcon = icon,
                                                    displayMode = RssDisplayMode.FULL_CONTENT,
                                                )
                                            }
                                        }.awaitAll()
                                        .toImmutableList()
                                }
                            }.getOrNull()

                        if (!linkSources.isNullOrEmpty()) {
                            RssState.RssSources(linkSources)
                        } else {
                            // Try to detect as a supported subscription instance.
                            val host =
                                rssCheck {
                                    Url(actualUrl).host
                                }.getOrNull() ?: throw IllegalArgumentException("Invalid URL: $url")

                            val instance = detectSubscriptionInstance(host)
                            if (instance == null) {
                                throw IllegalArgumentException("URL is not a valid RSS feed or Mastodon instance: $url")
                            }
                            instance
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

    private suspend fun detectSubscriptionInstance(host: String): RssState.SubscriptionInstance? =
        coroutineScope {
            platformRegistry.all
                .filter { it.subscriptionTimelineSpecs.isNotEmpty() }
                .firstNotNullOfOrNull { platform ->
                    val timelines =
                        platform.subscriptionTimelineSpecs
                            .map { spec ->
                                async {
                                    if (spec.isAvailable(host, Locale.language)) {
                                        spec.type
                                    } else {
                                        null
                                    }
                                }
                            }.awaitAll()
                            .filterNotNull()
                            .toImmutableList()

                    if (timelines.isEmpty()) {
                        return@firstNotNullOfOrNull null
                    }

                    RssState.SubscriptionInstance(
                        host = host,
                        instanceName = host,
                        icon =
                            rssCheck {
                                FaviconService.fetchIcon("https://$host/")
                            }.getOrNull(),
                        availableTimelines = timelines,
                    )
                }
        }
}

private inline fun <T> rssCheck(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }
