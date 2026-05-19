package dev.dimension.flare.ui.presenter.home

import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.datasource.microblog.MixedRemoteMediator
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.notSupported
import dev.dimension.flare.data.model.tab.GroupTimelineTabItemV2
import dev.dimension.flare.data.model.tab.TimelineMergePolicy
import dev.dimension.flare.data.model.tab.TimelinePresenterFactory
import dev.dimension.flare.data.model.tab.TimelineResolver
import dev.dimension.flare.data.model.tab.TimelineTabItemV2
import dev.dimension.flare.data.model.tab.isSystemHomeMixedTimeline
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.repository.homeTimelineTab
import dev.dimension.flare.data.repository.homeTimelineTabs
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
    id: String,
    private val fallbackSubTimelinePresenter: List<TimelinePresenter> = emptyList(),
    private val fallbackMergePolicy: TimelineMergePolicy = TimelineMergePolicy.TimePerPage,
) : TimelinePresenter(),
    KoinComponent {
    private val groupId = id

    public constructor(
        subTimelinePresenter: List<TimelinePresenter>,
        mergePolicy: TimelineMergePolicy = TimelineMergePolicy.TimePerPage,
    ) : this(
        id = "legacy_mixed_timeline",
        fallbackSubTimelinePresenter = subTimelinePresenter,
        fallbackMergePolicy = mergePolicy,
    )

    private val database: CacheDatabase by inject()
    private val appDataStore: AppDataStore by inject()
    private val timelineResolver: TimelineResolver by inject()
    private val timelinePresenterFactory: TimelinePresenterFactory by inject()

    init {
        bindTimelineTabItemId(id)
    }

    private val groupTabFlow: Flow<GroupTimelineTabItemV2?> by lazy {
        appDataStore
            .homeTimelineTab(groupId, timelineResolver)
            .map { it as? GroupTimelineTabItemV2 }
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
                val presenters = tabs?.map(timelinePresenterFactory::create) ?: fallbackSubTimelinePresenter
                if (presenters.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    combine(presenters.map { it.loader }) { it.toList() }
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
) : TimelinePresenter(),
    KoinComponent {
    private val groupId = id

    private val database: CacheDatabase by inject()
    private val appDataStore: AppDataStore by inject()
    private val timelineResolver: TimelineResolver by inject()
    private val timelinePresenterFactory: TimelinePresenterFactory by inject()

    init {
        bindTimelineTabItemId(id)
    }

    private val groupTabFlow: Flow<GroupTimelineTabItemV2?> by lazy {
        appDataStore
            .homeTimelineTab(groupId, timelineResolver)
            .map { it as? GroupTimelineTabItemV2 }
    }

    private val mergePolicyFlow: Flow<TimelineMergePolicy> by lazy {
        groupTabFlow
            .map { it?.mergePolicy ?: TimelineMergePolicy.TimePerPage }
            .distinctUntilChanged()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val subTimelineLoadersFlow: Flow<List<RemoteLoader<UiTimelineV2>>> by lazy {
        appDataStore.homeTimelineTabs(timelineResolver)
            .map { tabs ->
                tabs
                    .filterNot { it.isSystemHomeMixedTimeline }
                    .filter { it.enabled }
            }.distinctUntilChangedByTabIds()
            .flatMapLatest { tabs ->
                val presenters = tabs.map(timelinePresenterFactory::create)
                if (presenters.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    combine(presenters.map { it.loader }) { it.toList() }
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

private fun Flow<List<TimelineTabItemV2>>.distinctUntilChangedByTabIds(): Flow<List<TimelineTabItemV2>> =
    distinctUntilChanged { old, new ->
        old.map { it.id } == new.map { it.id }
    }
