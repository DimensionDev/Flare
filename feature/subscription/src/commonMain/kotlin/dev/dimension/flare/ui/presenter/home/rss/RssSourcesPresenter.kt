package dev.dimension.flare.ui.presenter.home.rss

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import dev.dimension.flare.data.database.app.model.SubscriptionType
import dev.dimension.flare.data.model.tab.RssTimelineData
import dev.dimension.flare.data.model.tab.SubscriptionTimelineData
import dev.dimension.flare.data.platform.RssTimelineSpecs
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.data.repository.SubscriptionRepository
import dev.dimension.flare.data.repository.SubscriptionSourceInput
import dev.dimension.flare.ui.model.UiRssSource
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.web.shared.WebPresenter
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@WebPresenter("rssSources")
public class RssSourcesPresenter :
    PresenterBase<RssSourcesPresenter.State>(),
    KoinComponent {
    private val subscriptionRepository by inject<SubscriptionRepository>()
    private val settingsRepository by inject<SettingsRepository>()

    @androidx.compose.runtime.Immutable
    public interface State {
        public val sources: ImmutableList<UiRssSource>

        public fun add(
            url: String,
            title: String,
            iconUrl: String?,
        )

        public fun delete(id: Int)
    }

    @Composable
    override fun body(): State {
        val scope = rememberCoroutineScope()
        val sources by remember {
            subscriptionRepository.observeAll().map {
                it
                    .toImmutableList()
            }
        }.collectAsState(persistentListOf())
        return object : State {
            override val sources = sources

            override fun add(
                url: String,
                title: String,
                iconUrl: String?,
            ) {
                scope.launch {
                    subscriptionRepository.upsert(
                        SubscriptionSourceInput(
                            url = url,
                            title = title,
                            icon = iconUrl,
                            lastUpdateMillis = 0,
                        ),
                    )
                }
            }

            override fun delete(id: Int) {
                scope.launch {
                    val source = subscriptionRepository.delete(id)
                    source?.let {
                        settingsRepository.removeHomeTimelineTabBySourceId(it.timelineSourceId())
                    }
                }
            }
        }
    }
}

private fun UiRssSource.timelineSourceId(): String =
    when (type) {
        SubscriptionType.RSS -> {
            RssTimelineSpecs.rss
                .itemId(RssTimelineData(url))
        }

        else -> {
            RssTimelineSpecs.subscription
                .itemId(
                    SubscriptionTimelineData(
                        subscriptionUrl = url,
                        subscriptionType = type,
                    ),
                )
        }
    }
