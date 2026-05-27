package dev.dimension.flare.ui.presenter.home.rss

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.dimension.flare.data.database.app.model.RssDisplayMode
import dev.dimension.flare.data.database.app.model.SubscriptionType
import dev.dimension.flare.data.repository.SubscriptionRepository
import dev.dimension.flare.data.repository.SubscriptionSourceInput
import dev.dimension.flare.data.repository.toUiRssSource
import dev.dimension.flare.model.vvo
import dev.dimension.flare.model.vvoHost
import dev.dimension.flare.model.vvoHostLong
import dev.dimension.flare.model.vvoHostShort
import dev.dimension.flare.ui.model.UiRssSource
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.ui.presenter.home.rss.CheckRssSourcePresenter.State.RssState
import io.ktor.http.Url
import io.ktor.http.buildUrl
import io.ktor.http.set
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class EditRssSourcePresenter(
    private val id: Int?,
) : PresenterBase<EditRssSourcePresenter.State>(),
    KoinComponent {
    private val subscriptionRepository: SubscriptionRepository by inject()
    private val scope by inject<CoroutineScope>()

    @Immutable
    public interface State {
        @Immutable
        public sealed interface RssInputState {
            @Immutable
            public interface RssFeed : RssInputState {
                public fun save(
                    title: String,
                    displayMode: RssDisplayMode,
                ): UiRssSource
            }

            @Immutable
            public interface RssSources : RssInputState {
                public fun save(
                    sources: List<UiRssSource>,
                    displayMode: RssDisplayMode,
                )
            }

            @Immutable
            public interface RssHub : RssInputState {
                public val checkState: UiState<RssState>

                public fun checkWithServer(server: String)

                public fun save(
                    title: String,
                    displayMode: RssDisplayMode,
                ): UiRssSource

                public val actualUrl: String
            }

            @Immutable
            public interface SubscriptionInstance : RssInputState {
                public val host: String
                public val instanceName: String?
                public val icon: String?
                public val availableTimelines: ImmutableList<SubscriptionType>

                public fun save(
                    selectedTypes: List<SubscriptionType>,
                    typeNames: Map<SubscriptionType, String>,
                ): List<UiRssSource>
            }
        }

        public val inputState: UiState<RssInputState>

        public fun checkUrl(value: String)

        public val data: UiState<UiRssSource>
        public val checkState: UiState<CheckRssSourcePresenter.State.RssState>

        public val canSave: Boolean
    }

    @Composable
    override fun body(): State {
        var url by remember { mutableStateOf("") }
        val checkRssSourcePresenterState =
            remember(
                url,
            ) {
                CheckRssSourcePresenter(
                    url,
                )
            }.body()
        val data by remember(id) {
            subscriptionRepository.observe(id ?: -1)
        }.collectAsUiState()
        val inputState =
            checkRssSourcePresenterState.state.map {
                when (it) {
                    is RssState.RssFeed -> {
                        object : State.RssInputState.RssFeed {
                            override fun save(
                                title: String,
                                displayMode: RssDisplayMode,
                            ): UiRssSource {
                                val data =
                                    SubscriptionSourceInput(
                                        id = id ?: 0,
                                        url = it.url,
                                        title = title,
                                        lastUpdateMillis = 0,
                                        displayMode = displayMode,
                                        icon = it.icon,
                                    )
                                scope.launch {
                                    subscriptionRepository.upsert(data)
                                }
                                return data.toUiRssSource()
                            }
                        }
                    }

                    RssState.RssHub -> {
                        var serverStr by remember { mutableStateOf("") }
                        val checkRssHubState =
                            remember(serverStr, url) {
                                val actualUrl =
                                    buildUrl {
                                        serverStr
                                            .removePrefix("https://")
                                            .removePrefix("http://")
                                            .let {
                                                set(host = it)
                                            }
                                        url
                                            .removePrefix("rsshub://")
                                            .let {
                                                set(path = it)
                                            }
                                        set(scheme = "https")
                                    }.toString()
                                CheckRssSourcePresenter(actualUrl)
                            }.body()
                        object : State.RssInputState.RssHub {
                            override val checkState = checkRssHubState.state

                            override val actualUrl =
                                buildUrl {
                                    serverStr
                                        .removePrefix("https://")
                                        .removePrefix("http://")
                                        .let {
                                            set(host = it)
                                        }
                                    url
                                        .removePrefix("rsshub://")
                                        .let {
                                            set(path = it)
                                        }
                                    set(scheme = "https")
                                }.toString()

                            override fun checkWithServer(server: String) {
                                serverStr = server
                            }

                            override fun save(
                                title: String,
                                displayMode: RssDisplayMode,
                            ): UiRssSource {
                                val data =
                                    SubscriptionSourceInput(
                                        id = 0,
                                        url = actualUrl,
                                        title = title,
                                        lastUpdateMillis = 0,
                                        displayMode = displayMode,
                                        icon = favIconUrl(actualUrl),
                                    )
                                scope.launch {
                                    subscriptionRepository.upsert(data)
                                }
                                return data.toUiRssSource()
                            }
                        }
                    }

                    is RssState.RssSources -> {
                        object : State.RssInputState.RssSources {
                            override fun save(
                                sources: List<UiRssSource>,
                                displayMode: RssDisplayMode,
                            ) {
                                scope.launch {
                                    subscriptionRepository.upsertAll(
                                        sources.map {
                                            SubscriptionSourceInput(
                                                id = it.id,
                                                url = it.url,
                                                title = it.title ?: "",
                                                lastUpdateMillis = 0,
                                                displayMode = displayMode,
                                                icon = it.favIcon,
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }

                    is RssState.SubscriptionInstance -> {
                        object : State.RssInputState.SubscriptionInstance {
                            override val host = it.host
                            override val instanceName = it.instanceName
                            override val icon = it.icon
                            override val availableTimelines = it.availableTimelines

                            override fun save(
                                selectedTypes: List<SubscriptionType>,
                                typeNames: Map<SubscriptionType, String>,
                            ): List<UiRssSource> {
                                val subscriptions =
                                    selectedTypes.map { type ->
                                        val typeName = typeNames[type] ?: type.name
                                        SubscriptionSourceInput(
                                            id = 0,
                                            url = it.host,
                                            title = "${it.instanceName ?: it.host} - $typeName",
                                            lastUpdateMillis = 0,
                                            displayMode = RssDisplayMode.FULL_CONTENT,
                                            icon = it.icon,
                                            type = type,
                                        )
                                    }
                                scope.launch {
                                    subscriptionRepository.upsertAll(subscriptions)
                                }
                                return subscriptions.map { it.toUiRssSource() }
                            }
                        }
                    }
                }
            }
        val canSave =
            when (val state = checkRssSourcePresenterState.state) {
                is UiState.Success -> {
                    when (val rssState = state.data) {
                        is RssState.RssFeed -> {
                            true
                        }

                        RssState.RssHub -> {
                            when (val inputState = inputState) {
                                is State.RssInputState.RssHub -> {
                                    inputState.checkState is UiState.Success
                                }

                                else -> {
                                    false
                                }
                            }
                        }

                        is RssState.RssSources -> {
                            rssState.sources.isNotEmpty()
                        }

                        is RssState.SubscriptionInstance -> {
                            rssState.availableTimelines.isNotEmpty()
                        }
                    }
                }

                else -> {
                    false
                }
            }
        return object : State {
            override val checkState = checkRssSourcePresenterState.state

            override val inputState = inputState

            override val canSave = canSave

            override fun checkUrl(value: String) {
                url = value
            }

            override val data: UiState<UiRssSource>
                get() = data
        }
    }
}

private fun favIconUrl(url: String): String {
    val parsedUrl =
        if (url.startsWith("http")) {
            Url(url)
        } else {
            Url("https://$url")
        }
    return when (parsedUrl.host) {
        "bsky.social" -> {
            "https://web-cdn.bsky.app/static/apple-touch-icon.png"
        }

        in listOf(vvo, vvoHostShort, vvoHost, vvoHostLong) -> {
            "https://upload.wikimedia.org/wikipedia/en/thumb/6/6e/Sina_Weibo.svg/2560px-Sina_Weibo.svg.png"
        }

        else -> {
            "https://${parsedUrl.host}/favicon.ico"
        }
    }
}
