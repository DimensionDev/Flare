package dev.dimension.flare.ui.presenter.home.rss

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.database.app.model.DbRssSources
import dev.dimension.flare.ui.model.UiRssSource
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.mapper.render
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.ui.presenter.home.rss.CheckRssSourcePresenter.State.RssState
import io.ktor.http.buildUrl
import io.ktor.http.set
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class EditRssSourcePresenter(
    private val id: Int?,
) : PresenterBase<EditRssSourcePresenter.State>(),
    KoinComponent {
    private val appDatabase by inject<AppDatabase>()
    private val scope by inject<CoroutineScope>()

    @Immutable
    public interface State {
        @Immutable
        public sealed interface RssInputState {
            @Immutable
            public interface RssFeed : RssInputState {
                public fun save(title: String)
            }

            @Immutable
            public interface RssSources : RssInputState {
                public fun save(sources: List<UiRssSource>)
            }

            @Immutable
            public interface RssHub : RssInputState {
                public val checkState: UiState<RssState>

                public fun checkWithServer(server: String)

                public fun save(title: String)

                public val actualUrl: String
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
            appDatabase
                .rssSourceDao()
                .get(id ?: -1)
                .map {
                    it.render()
                }
        }.collectAsUiState()
        val inputState =
            checkRssSourcePresenterState.state.map {
                when (it) {
                    is RssState.RssFeed ->
                        object : State.RssInputState.RssFeed {
                            override fun save(title: String) {
                                scope.launch {
                                    appDatabase
                                        .rssSourceDao()
                                        .insert(
                                            DbRssSources(
                                                id = id ?: 0,
                                                url = url,
                                                title = title,
                                                lastUpdate = 0,
                                            ),
                                        )
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

                            override val actualUrl = buildUrl {
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

                            override fun save(title: String) {
                                scope.launch {
                                    appDatabase
                                        .rssSourceDao()
                                        .insert(
                                            DbRssSources(
                                                id = 0,
                                                url = actualUrl,
                                                title = title,
                                                lastUpdate = 0,
                                            ),
                                        )
                                }
                            }
                        }
                    }

                    is RssState.RssSources ->
                        object : State.RssInputState.RssSources {
                            override fun save(sources: List<UiRssSource>) {
                                scope.launch {
                                    appDatabase
                                        .rssSourceDao()
                                        .insertAll(
                                            sources.map {
                                                DbRssSources(
                                                    id = it.id,
                                                    url = it.url,
                                                    title = it.title ?: "",
                                                    lastUpdate = 0,
                                                )
                                            },
                                        )
                                }
                            }
                        }
                }
            }
        val canSave =
            when (val state = checkRssSourcePresenterState.state) {
                is UiState.Success ->
                    when (state.data) {
                        is RssState.RssFeed -> true
                        RssState.RssHub -> when (val inputState = inputState) {
                            is State.RssInputState.RssHub ->
                                inputState.checkState is UiState.Success

                            else -> false
                        }

                        is RssState.RssSources -> state.data.sources.isNotEmpty()
                    }

                else -> false
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
