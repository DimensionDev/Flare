package dev.dimension.flare.ui.presenter.home.rss

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.dimension.flare.data.database.app.model.RssDisplayMode
import dev.dimension.flare.data.database.app.model.SubscriptionType
import dev.dimension.flare.data.network.rss.Readability
import dev.dimension.flare.data.network.rss.recommendRssDisplayMode
import dev.dimension.flare.data.network.rss.toRssWebUrl
import dev.dimension.flare.data.repository.SubscriptionRepository
import dev.dimension.flare.data.repository.SubscriptionSourceInput
import dev.dimension.flare.data.repository.toUiRssSource
import dev.dimension.flare.di.koinInject
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
import dev.dimension.flare.web.shared.WebPresenter
import io.ktor.http.Url
import io.ktor.http.buildUrl
import io.ktor.http.set
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

@WebPresenter("editRssSource")
public class EditRssSourcePresenter(
    private val id: Int?,
    private val initialDisplayMode: RssDisplayMode? = null,
) : PresenterBase<EditRssSourcePresenter.State>() {
    private var checkSource: (String) -> CheckRssSourcePresenter = { CheckRssSourcePresenter(it) }
    private var extract: suspend (String) -> dev.dimension.flare.data.network.rss.DocumentData = Readability()::extract

    internal constructor(
        id: Int?,
        initialDisplayMode: RssDisplayMode?,
        checkSource: (String) -> CheckRssSourcePresenter,
        extract: suspend (String) -> dev.dimension.flare.data.network.rss.DocumentData,
    ) : this(id, initialDisplayMode) {
        this.checkSource = checkSource
        this.extract = extract
    }

    private val subscriptionRepository: SubscriptionRepository by koinInject()
    private val scope by koinInject<CoroutineScope>()

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
                public fun save(sources: List<UiRssSource>)
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
        public val rssHubCheckState: UiState<CheckRssSourcePresenter.State.RssState>?
        public val actualRssHubUrl: String

        public val canSave: Boolean
        public val displayMode: RssDisplayMode
        public val readingModes: ImmutableList<RssReadingMode>

        public fun setDisplayMode(value: RssDisplayMode)

        public fun setSourceDisplayMode(
            url: String,
            value: RssDisplayMode,
        )

        public fun selectSources(urls: String)

        public fun checkRssHubServer(value: String)

        public fun saveRssFeed(
            title: String,
            displayMode: RssDisplayMode,
        )

        public fun saveRssHub(
            title: String,
            displayMode: RssDisplayMode,
        )

        public fun saveSourcesByUrl(selectedUrls: String)

        public fun saveSubscriptionTypes(selectedTypes: String)
    }

    @Composable
    override fun body(): State {
        var url by remember { mutableStateOf("") }
        var rssHubServer by remember { mutableStateOf("") }
        val checkPresenter =
            remember(
                url,
            ) {
                checkSource(url)
            }
        val checkRssSourcePresenterState = key(checkPresenter) { checkPresenter.body() }
        val data by remember(id) {
            subscriptionRepository.observe(id ?: -1)
        }.collectAsUiState()
        val actualRssHubUrl =
            remember(rssHubServer, url) {
                buildRssHubUrl(
                    server = rssHubServer,
                    path = url,
                )
            }
        val rssHubPresenter =
            if (
                rssHubServer.isNotBlank() &&
                (checkRssSourcePresenterState.state as? UiState.Success)?.data == RssState.RssHub
            ) {
                remember(actualRssHubUrl) {
                    checkSource(actualRssHubUrl)
                }
            } else {
                null
            }
        val rssHubCheckState = rssHubPresenter?.let { key(it) { it.body().state } }
        var selectedUrls by remember(url) { mutableStateOf(emptySet<String>()) }
        val modeSelection = remember(url, actualRssHubUrl) { RssReadingModeSelection() }
        val probes = remember(modeSelection) { Semaphore(3) }
        val rssState = (checkRssSourcePresenterState.state as? UiState.Success)?.data
        val hubFeed = (rssHubCheckState as? UiState.Success)?.data as? RssState.RssFeed
        val readingUrls =
            when (rssState) {
                is RssState.RssFeed -> listOf(rssState.url)
                is RssState.RssSources -> rssState.sources.map { it.url }
                RssState.RssHub -> listOfNotNull(hubFeed?.url)
                else -> emptyList()
            }
        val preset = (data as? UiState.Success)?.data?.displayMode ?: initialDisplayMode
        val activeUrl = hubFeed?.url ?: (rssState as? RssState.RssFeed)?.url ?: url.toRssWebUrl()
        val readingModes =
            readingUrls
                .map { readingUrl ->
                    val selected = rssState !is RssState.RssSources || readingUrl in selectedUrls
                    val feed = (rssHubPresenter ?: checkPresenter).feeds[readingUrl]
                    LaunchedEffect(modeSelection, readingUrl, selected, feed) {
                        if (id == null && initialDisplayMode == null && selected && feed != null) {
                            probes.withPermit { modeSelection.recommend(readingUrl, recommendRssDisplayMode(feed, extract)) }
                        }
                    }
                    modeSelection.state(readingUrl, preset, checking = selected && id == null)
                }.toImmutableList()
        val displayMode = modeSelection.state(activeUrl, preset, checking = id == null).displayMode
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
                        object : State.RssInputState.RssHub {
                            override val checkState = rssHubCheckState ?: UiState.Loading()

                            override val actualUrl = actualRssHubUrl

                            override fun checkWithServer(server: String) {
                                rssHubServer = server
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
                            override fun save(sources: List<UiRssSource>) {
                                val inputs =
                                    sources.map { source ->
                                        SubscriptionSourceInput(
                                            id = source.id,
                                            url = source.url,
                                            title = source.title.orEmpty(),
                                            lastUpdateMillis = 0,
                                            displayMode = modeSelection.state(source.url, preset).displayMode,
                                            icon = source.favIcon,
                                        )
                                    }
                                scope.launch { subscriptionRepository.upsertAll(inputs) }
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
                            rssHubCheckState is UiState.Success &&
                                rssHubCheckState.data is RssState.RssFeed
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
            override val rssHubCheckState = rssHubCheckState
            override val actualRssHubUrl = actualRssHubUrl
            override val displayMode = displayMode
            override val readingModes = readingModes

            override fun setDisplayMode(value: RssDisplayMode) {
                modeSelection.select(activeUrl, value)
            }

            override fun setSourceDisplayMode(
                url: String,
                value: RssDisplayMode,
            ) {
                modeSelection.select(url, value)
            }

            override fun selectSources(urls: String) {
                selectedUrls = urls.splitCsv().toSet()
            }

            override fun checkUrl(value: String) {
                if (url != value.trim()) {
                    url = value.trim()
                    rssHubServer = ""
                }
            }

            override fun checkRssHubServer(value: String) {
                rssHubServer = value
            }

            override fun saveRssFeed(
                title: String,
                displayMode: RssDisplayMode,
            ) {
                val rssState = (checkState as? UiState.Success)?.data as? RssState.RssFeed ?: return
                scope.launch {
                    subscriptionRepository.upsert(
                        SubscriptionSourceInput(
                            id = id ?: 0,
                            url = rssState.url,
                            title = title.ifBlank { rssState.title },
                            lastUpdateMillis = 0,
                            displayMode = displayMode,
                            icon = rssState.icon,
                        ),
                    )
                }
            }

            override fun saveRssHub(
                title: String,
                displayMode: RssDisplayMode,
            ) {
                val rssState = (rssHubCheckState as? UiState.Success)?.data as? RssState.RssFeed ?: return
                scope.launch {
                    subscriptionRepository.upsert(
                        SubscriptionSourceInput(
                            id = 0,
                            url = actualRssHubUrl,
                            title = title.ifBlank { rssState.title },
                            lastUpdateMillis = 0,
                            displayMode = displayMode,
                            icon = rssState.icon ?: favIconUrl(actualRssHubUrl),
                        ),
                    )
                }
            }

            override fun saveSourcesByUrl(selectedUrls: String) {
                val rssState = (checkState as? UiState.Success)?.data as? RssState.RssSources ?: return
                val selected = selectedUrls.splitCsv().toSet()
                val inputs =
                    rssState.sources.filter { it.url in selected }.map { source ->
                        SubscriptionSourceInput(
                            id = source.id,
                            url = source.url,
                            title = source.title,
                            lastUpdateMillis = 0,
                            displayMode = modeSelection.state(source.url, preset).displayMode,
                            icon = source.favIcon,
                        )
                    }
                scope.launch { subscriptionRepository.upsertAll(inputs) }
            }

            override fun saveSubscriptionTypes(selectedTypes: String) {
                val rssState = (checkState as? UiState.Success)?.data as? RssState.SubscriptionInstance ?: return
                val selected =
                    selectedTypes
                        .splitCsv()
                        .mapNotNull { runCatching { SubscriptionType.valueOf(it) }.getOrNull() }
                scope.launch {
                    subscriptionRepository.upsertAll(
                        selected.map { type ->
                            SubscriptionSourceInput(
                                id = 0,
                                url = rssState.host,
                                title = "${rssState.instanceName ?: rssState.host} - ${type.name}",
                                lastUpdateMillis = 0,
                                displayMode = RssDisplayMode.FULL_CONTENT,
                                icon = rssState.icon,
                                type = type,
                            )
                        },
                    )
                }
            }

            override val data: UiState<UiRssSource>
                get() = data
        }
    }
}

private fun buildRssHubUrl(
    server: String,
    path: String,
): String =
    buildUrl {
        server
            .removePrefix("https://")
            .removePrefix("http://")
            .let {
                set(host = it)
            }
        path
            .removePrefix("rsshub://")
            .let {
                set(path = it)
            }
        set(scheme = "https")
    }.toString()

private fun String.splitCsv(): List<String> =
    split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }

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
