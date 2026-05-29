package dev.dimension.flare.ui.presenter.home.rss

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import dev.dimension.flare.common.Locale
import dev.dimension.flare.data.database.app.model.RssDisplayMode
import dev.dimension.flare.data.database.app.model.SubscriptionType
import dev.dimension.flare.data.network.FaviconService
import dev.dimension.flare.data.network.rss.RssService
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Instant

public class CheckRssSourcePresenter(
    private val url: String,
) : PresenterBase<CheckRssSourcePresenter.State>(),
    KoinComponent {
    private val platformRegistry: PlatformRegistry by inject()

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
                        val linkSources =
                            runCatching {
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
                                                    id = 0,
                                                    url = it,
                                                    title = RssService.fetch(it).title.trim(),
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
                                runCatching {
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
            val platform =
                platformRegistry.all.firstOrNull {
                    runCatching {
                        it.detector.detect(host)
                    }.getOrNull() != null
                } ?: return@coroutineScope null

            val instanceInfo =
                async {
                    runCatching {
                        platform.instanceMetadata(host).instance
                    }.getOrNull()
                }

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
                throw IllegalArgumentException("No accessible timelines found on $host (authentication may be required)")
            }

            val instance = instanceInfo.await()
            RssState.SubscriptionInstance(
                host = host,
                instanceName = instance?.name,
                icon =
                    instance?.iconUrl ?: runCatching {
                        FaviconService.fetchIcon("https://$host/")
                    }.getOrNull(),
                availableTimelines = timelines,
            )
        }
}
