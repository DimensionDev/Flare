package dev.dimension.flare.ui.presenter.home

import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.datasource.microblog.MixedRemoteMediator
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.notSupported
import dev.dimension.flare.data.model.tab.TimelineMergePolicy
import dev.dimension.flare.data.model.tab.TimelineResolver
import dev.dimension.flare.data.model.tab.UiGroupTimelineTabItem
import dev.dimension.flare.data.model.tab.UiTimelineTabItem
import dev.dimension.flare.data.model.tab.isSystemHomeMixedTimeline
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.di.koinInject
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

public class MixedTimelinePresenter(
    id: String,
    private val fallbackSubTimelinePresenter: List<TimelinePresenter> = emptyList(),
    private val fallbackMergePolicy: TimelineMergePolicy = TimelineMergePolicy.TimePerPage,
) : TimelinePresenter(tabId = id) {
    private val groupId = id

    public constructor(
        subTimelinePresenter: List<TimelinePresenter>,
        mergePolicy: TimelineMergePolicy = TimelineMergePolicy.TimePerPage,
    ) : this(
        id = "legacy_mixed_timeline",
        fallbackSubTimelinePresenter = subTimelinePresenter,
        fallbackMergePolicy = mergePolicy,
    )

    private val database: CacheDatabase by koinInject()
    private val settingsRepository: SettingsRepository by koinInject()
    private val timelineResolver: TimelineResolver by koinInject()

    private val groupTabFlow: Flow<UiGroupTimelineTabItem?> by lazy {
        settingsRepository
            .homeTimelineTab(groupId)
            .map { it as? UiGroupTimelineTabItem }
    }

    private val mergePolicyFlow: Flow<TimelineMergePolicy> by lazy {
        groupTabFlow
            .map { it?.mergePolicy ?: fallbackMergePolicy }
            .distinctUntilChanged()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val subTimelineLoadersFlow: Flow<List<RemoteLoader<UiTimelineV2>>> by lazy {
        groupTabFlow
            .map { group ->
                group
                    ?.children
                    ?.filter { it.enabled }
            }.distinctUntilChanged { old, new ->
                old.orEmpty().map { it.id } == new.orEmpty().map { it.id }
            }.flatMapLatest { tabs ->
                if (tabs == null) {
                    if (fallbackSubTimelinePresenter.isEmpty()) {
                        flowOf(emptyList())
                    } else {
                        combine(fallbackSubTimelinePresenter.map { it.loader }) { it.toList() }
                    }
                } else if (tabs.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    combine(tabs.map { timelineResolver.resolveLoader(it) }) { it.toList() }
                }
            }
    }

    override val loader: Flow<RemoteLoader<UiTimelineV2>>
        get() =
            combine(subTimelineLoadersFlow, mergePolicyFlow) { loaders, mergePolicy ->
                if (loaders.isEmpty()) {
                    notSupported()
                } else {
                    MixedRemoteMediator(
                        database = database,
                        mediators = loaders.filterIsInstance<CacheableRemoteLoader<UiTimelineV2>>(),
                        mergePolicy = mergePolicy,
                    )
                }
            }
}

public class SystemHomeMixedTimelinePresenter(
    id: String,
    isHomeTimeline: Boolean = false,
) : TimelinePresenter(tabId = id, isHomeTimeline = isHomeTimeline) {
    private val groupId = id

    private val database: CacheDatabase by koinInject()
    private val settingsRepository: SettingsRepository by koinInject()
    private val timelineResolver: TimelineResolver by koinInject()

    private val groupTabFlow: Flow<UiGroupTimelineTabItem?> by lazy {
        settingsRepository
            .homeTimelineTab(groupId)
            .map { it as? UiGroupTimelineTabItem }
    }

    private val mergePolicyFlow: Flow<TimelineMergePolicy> by lazy {
        groupTabFlow
            .map { it?.mergePolicy ?: TimelineMergePolicy.TimePerPage }
            .distinctUntilChanged()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val subTimelineLoadersFlow: Flow<List<RemoteLoader<UiTimelineV2>>> by lazy {
        settingsRepository.homeTimelineTabs
            .map { tabs ->
                tabs
                    .filterNot { it.isSystemHomeMixedTimeline }
                    .filter { it.enabled }
            }.distinctUntilChangedByTabIds()
            .flatMapLatest { tabs ->
                if (tabs.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    combine(tabs.map { timelineResolver.resolveLoader(it) }) { it.toList() }
                }
            }
    }

    override val loader: Flow<RemoteLoader<UiTimelineV2>>
        get() =
            combine(subTimelineLoadersFlow, mergePolicyFlow) { loaders, mergePolicy ->
                if (loaders.isEmpty()) {
                    notSupported()
                } else {
                    MixedRemoteMediator(
                        database = database,
                        mediators = loaders.filterIsInstance<CacheableRemoteLoader<UiTimelineV2>>(),
                        mergePolicy = mergePolicy,
                    )
                }
            }
}

private fun Flow<List<UiTimelineTabItem>>.distinctUntilChangedByTabIds(): Flow<List<UiTimelineTabItem>> =
    distinctUntilChanged { old, new ->
        old.map { it.id } == new.map { it.id }
    }
