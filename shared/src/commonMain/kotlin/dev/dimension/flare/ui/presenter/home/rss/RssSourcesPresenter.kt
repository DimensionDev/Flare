package dev.dimension.flare.ui.presenter.home.rss

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.database.app.model.DbRssSources
import dev.dimension.flare.data.database.app.model.SubscriptionType
import dev.dimension.flare.data.model.tab.TimelineSlot
import dev.dimension.flare.data.model.tab.TimelineSlotContent
import dev.dimension.flare.data.platform.RssTimelineSpecs
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.ui.model.UiRssSource
import dev.dimension.flare.ui.model.mapper.render
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class RssSourcesPresenter :
    PresenterBase<RssSourcesPresenter.State>(),
    KoinComponent {
    private val appDatabase by inject<AppDatabase>()
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
            appDatabase.rssSourceDao().getAll().map {
                it
                    .map {
                        it.render()
                    }.toImmutableList()
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
                    appDatabase.rssSourceDao().insert(
                        DbRssSources(
                            url = url,
                            title = title,
                            lastUpdate = 0,
                            icon = iconUrl,
                        ),
                    )
                }
            }

            override fun delete(id: Int) {
                scope.launch {
                    val source =
                        appDatabase
                            .rssSourceDao()
                            .getAll()
                            .first()
                            .firstOrNull { it.id == id }
                    appDatabase.rssSourceDao().delete(id)
                    source?.let {
                        settingsRepository.removeHomeTimelineTabForRssSource(it)
                    }
                }
            }
        }
    }
}

private suspend fun SettingsRepository.removeHomeTimelineTabForRssSource(source: DbRssSources) {
    val sourceId =
        when (source.type) {
            SubscriptionType.RSS ->
                RssTimelineSpecs.rss
                    .target(RssTimelineSpecs.RssData(source.url))
                    .id

            else ->
                RssTimelineSpecs.subscription
                    .target(
                        RssTimelineSpecs.SubscriptionData(
                            subscriptionUrl = source.url,
                            subscriptionType = source.type,
                        ),
                    ).id
        }
    updateTabSettingsV2 {
        copy(
            homeSlots =
                homeSlots
                    .mapNotNull { it.removeSource(sourceId) }
                    .distinctBy { it.id },
        )
    }
}

private fun TimelineSlot.removeSource(sourceId: String): TimelineSlot? =
    when (val slotContent = content) {
        is TimelineSlotContent.Source -> {
            if (slotContent.source.id == sourceId) {
                null
            } else {
                this
            }
        }

        is TimelineSlotContent.Group -> {
            val sanitizedChildren =
                slotContent.children
                    .mapNotNull { it.removeSource(sourceId) }
                    .distinctBy { it.id }
            if (sanitizedChildren.isEmpty()) {
                null
            } else if (sanitizedChildren == slotContent.children) {
                this
            } else {
                copy(content = slotContent.copy(children = sanitizedChildren))
            }
        }
    }
