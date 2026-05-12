package dev.dimension.flare.ui.presenter.home

import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.datasource.microblog.MixedRemoteMediator
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.notSupported
import dev.dimension.flare.data.model.tab.TimelineMergePolicy
import dev.dimension.flare.data.model.tab.TimelineTabItemV2
import dev.dimension.flare.data.model.tab.isSystemHomeMixedTimeline
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class MixedTimelinePresenter(
    private val subTimelinePresenter: List<TimelinePresenter>,
    private val mergePolicy: TimelineMergePolicy = TimelineMergePolicy.TimePerPage,
) : TimelinePresenter(),
    KoinComponent {
    private val database: CacheDatabase by inject()
    override val loader: Flow<RemoteLoader<UiTimelineV2>>
        get() =
            combine(subTimelinePresenter.map { it.loader }) {
                MixedRemoteMediator(
                    database = database,
                    mediators = it.filterIsInstance<CacheableRemoteLoader<UiTimelineV2>>(),
                    mergePolicy = mergePolicy,
                )
            }
}

public class SystemHomeMixedTimelinePresenter(
    private val mergePolicy: TimelineMergePolicy = TimelineMergePolicy.TimePerPage,
) : TimelinePresenter(),
    KoinComponent {
    private val database: CacheDatabase by inject()
    private val settingsRepository: SettingsRepository by inject()

    @OptIn(ExperimentalCoroutinesApi::class)
    override val loader: Flow<RemoteLoader<UiTimelineV2>>
        get() =
            settingsRepository.homeTimelineTabs
                .map { tabs ->
                    tabs
                        .filterNot { it.isSystemHomeMixedTimeline }
                        .filter { it.enabled }
                }.distinctUntilChangedByTabIds()
                .flatMapLatest { tabs ->
                    if (tabs.isEmpty()) {
                        flowOf(notSupported())
                    } else {
                        combine(tabs.map { it.createPresenter().loader }) {
                            MixedRemoteMediator(
                                database = database,
                                mediators = it.filterIsInstance<CacheableRemoteLoader<UiTimelineV2>>(),
                                mergePolicy = mergePolicy,
                            )
                        }
                    }
                }
}

private fun Flow<List<TimelineTabItemV2>>.distinctUntilChangedByTabIds(): Flow<List<TimelineTabItemV2>> =
    distinctUntilChanged { old, new ->
        old.map { it.id } == new.map { it.id }
    }
