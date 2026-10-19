package dev.dimension.flare.ui.presenter.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadState
import androidx.paging.Pager
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.filter
import androidx.paging.map
import dev.dimension.flare.common.InAppNotification
import dev.dimension.flare.common.Message
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.PlatformDispatchers
import dev.dimension.flare.common.emptyFlow
import dev.dimension.flare.common.onEmpty
import dev.dimension.flare.common.onError
import dev.dimension.flare.common.onSuccess
import dev.dimension.flare.common.toPagingState
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.model.TranslationDisplayOptions
import dev.dimension.flare.data.datasource.microblog.offsetPagingConfig
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.NotSupportRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.OffsetFromStartPagingKey
import dev.dimension.flare.data.datasource.microblog.paging.OffsetFromStartPagingSource
import dev.dimension.flare.data.datasource.microblog.paging.PostContextLoader
import dev.dimension.flare.data.datasource.microblog.paging.PostContextRemoteMediator
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.TimelineDbPageCache
import dev.dimension.flare.data.datasource.microblog.paging.TimelineDbPageLoader
import dev.dimension.flare.data.datasource.microblog.paging.TimelinePageItem
import dev.dimension.flare.data.datasource.microblog.paging.TimelineRemoteMediator
import dev.dimension.flare.data.datasource.microblog.paging.TimelineViewport
import dev.dimension.flare.data.datasource.microblog.paging.notSupported
import dev.dimension.flare.data.datasource.microblog.paging.toTimelinePagingSource
import dev.dimension.flare.data.datasource.microblog.pagingConfig
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.model.tab.TimelineFilterConfig
import dev.dimension.flare.data.model.tab.TimelinePostContent
import dev.dimension.flare.data.model.tab.TimelinePostKind
import dev.dimension.flare.data.repository.KeywordFilterPattern
import dev.dimension.flare.data.repository.LocalFilterRepository
import dev.dimension.flare.data.repository.LoginExpiredException
import dev.dimension.flare.data.repository.MxgaRepository
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.data.repository.isMxgaMatch
import dev.dimension.flare.data.translation.PreTranslationService
import dev.dimension.flare.data.translation.TranslationSettingsSupport
import dev.dimension.flare.di.koinInject
import dev.dimension.flare.model.PlatformRegistry
import dev.dimension.flare.model.ReferenceType
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.asTimelinePostItem
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.ui.presenter.TimelineReadingState
import dev.dimension.flare.web.shared.WebPresenter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

public class TimelinePresenterOptions(
    public val allowLongTextTranslationDisplay: (RemoteLoader<UiTimelineV2>) -> Boolean = { false },
    public val transform: suspend (UiTimelineV2) -> UiTimelineV2 = { it },
)

@OptIn(ExperimentalPagingApi::class)
@WebPresenter(name = "timeline", creatable = false)
public open class TimelinePresenter : PresenterBase<TimelineState> {
    private val baseLoader: Flow<RemoteLoader<UiTimelineV2>>
    public open val loader: Flow<RemoteLoader<UiTimelineV2>>
        get() = baseLoader
    private val options: TimelinePresenterOptions

    private val database: CacheDatabase by koinInject()
    private val appDataStore: AppDataStore by koinInject()
    private val preTranslationService: PreTranslationService by koinInject()
    private val settingsRepository: SettingsRepository by koinInject()
    private val mxgaRepository: MxgaRepository by koinInject()
    private val platformRegistry: PlatformRegistry by koinInject()

    private val localFilterRepository: LocalFilterRepository by koinInject()
    private val inAppNotification: InAppNotification by koinInject()

    private val filterFlow: Flow<List<KeywordFilterPattern>> by lazy {
        localFilterRepository.getFlow(forTimeline = true)
    }

    private val timelineTabItemId: String?
    private val isHomeTimeline: Boolean
    private val contextMediator = MutableStateFlow<PostContextRemoteMediator?>(null)
    private val appScope: CoroutineScope by koinInject()
    private val viewport = MutableStateFlow<TimelineViewport?>(null)
    private val homeMediator = MutableStateFlow<TimelineRemoteMediator?>(null)
    private val gapActivity = MutableStateFlow<Map<String, LoadState>>(emptyMap())
    private val startupRefresh = MutableStateFlow(false)
    internal var sourceActive: Flow<Boolean> = flowOf(true)
    internal var sourceAccounts: Set<dev.dimension.flare.model.MicroBlogKey> = emptySet()

    private val timelineFilterConfigFlow: Flow<TimelineFilterConfig> by lazy {
        observeTimelineFilterConfig(
            settingsRepository = settingsRepository,
            timelineTabItemId = timelineTabItemId,
        )
    }

    private val mxgaEnabledFlow: Flow<Boolean> by lazy {
        settingsRepository.appSettings
            .map { it.mxgaEnabled }
            .distinctUntilChanged()
    }

    private val translationSettingsFlow: Flow<TranslationDisplayOptions> by lazy {
        TranslationSettingsSupport.displayOptionsFlow(appDataStore)
    }

    public constructor(
        tabId: String? = null,
        loader: Flow<RemoteLoader<UiTimelineV2>> = flowOf(notSupported()),
        options: TimelinePresenterOptions = TimelinePresenterOptions(),
        isHomeTimeline: Boolean = false,
    ) : super() {
        this.baseLoader = loader
        this.options = options
        this.timelineTabItemId = tabId
        this.isHomeTimeline = isHomeTimeline
    }

    internal open fun allowLongTextTranslationDisplay(loader: RemoteLoader<UiTimelineV2>): Boolean =
        options.allowLongTextTranslationDisplay(loader)

    @OptIn(ExperimentalCoroutinesApi::class)
    internal fun createPager(scope: CoroutineScope): Flow<PagingData<UiTimelineV2>> =
        loader
            .combine(
                if (isHomeTimeline) {
                    settingsRepository.appSettings
                        .map {
                            it.rememberHomeTimelinePosition
                        }.distinctUntilChanged()
                } else {
                    flowOf(false)
                },
            ) { source, enabled ->
                source to
                    enabled
            }.combine(sourceActive) { (source, enabled), active -> (if (active) source else notSupported()) to enabled }
            .combine(database.timelineResets) { source, _ -> source }
            .flatMapLatest { (remoteLoader, enabled) ->
                flow {
                    coroutineScope {
                        val sourceScope = this
                        viewport.value?.deactivate()
                        viewport.value = null
                        gapActivity.value = emptyMap()
                        contextMediator.value = null
                        homeMediator.value = null
                        startupRefresh.value = false
                        emitAll(
                            when (remoteLoader) {
                                is NotSupportRemoteLoader<UiTimelineV2> -> {
                                    PagingData.emptyFlow(isError = false)
                                }

                                is CacheableRemoteLoader<UiTimelineV2> -> {
                                    cachePager(
                                        loader = remoteLoader,
                                        scope = sourceScope,
                                        rememberPosition = isHomeTimeline && timelineTabItemId != null && enabled,
                                    ).cachedIn(sourceScope).flatMapLatest { pagingData ->
                                        translationSettingsFlow
                                            .combine(gapActivity) { translationDisplayOptions, activity ->
                                                withContext(PlatformDispatchers.IO) {
                                                    pagingData.map { item ->
                                                        val ui = item.toUi(translationDisplayOptions)
                                                        if (ui is UiTimelineV2.Gap) {
                                                            val mediator = homeMediator.value
                                                            ui.copy(
                                                                loading = activity[ui.stableItemKey] is LoadState.Loading,
                                                                failed = activity[ui.stableItemKey] is LoadState.Error,
                                                                onActivate = { onInserted ->
                                                                    loadGap(ui, mediator, sourceScope, onInserted)
                                                                },
                                                            )
                                                        } else {
                                                            ui
                                                        }
                                                    }
                                                }
                                            }
                                    }
                                }

                                else -> {
                                    networkPager(
                                        loader = remoteLoader,
                                    ).cachedIn(sourceScope)
                                }
                            }.flatMapLatest { pager ->
                                combine(
                                    filterFlow,
                                    timelineFilterConfigFlow,
                                    mxgaEnabledFlow,
                                    mxgaRepository.snapshot,
                                ) { filterList, timelineFilterConfig, mxgaEnabled, mxgaSnapshot ->
                                    val visible: (UiTimelineV2) -> Boolean = { item ->
                                        item.matchesKeywordFilters(filterList) && item.matchesTimelineFilter(timelineFilterConfig) &&
                                            (!mxgaEnabled || !item.isMxgaMatch(mxgaSnapshot, platformRegistry))
                                    }
                                    viewport.value?.setVisibility(
                                        listOf(filterList, timelineFilterConfig, mxgaEnabled, mxgaSnapshot),
                                        visible,
                                    )
                                    pager
                                        .filter { item ->
                                            item is UiTimelineV2.Gap || visible(item)
                                        }.map {
                                            if (it is UiTimelineV2.Gap) it else transform(it)
                                        }
                                }
                            },
                        )
                    }
                }
            }.catch {
                emitAll(PagingData.emptyFlow(isError = true))
            }.cachedIn(scope)

    private fun cachePager(
        loader: CacheableRemoteLoader<UiTimelineV2>,
        scope: CoroutineScope,
        rememberPosition: Boolean,
    ): Flow<PagingData<TimelinePageItem>> =
        flow {
            val preferences = settingsRepository.appSettings.first()
            val allowLongText = allowLongTextTranslationDisplay(loader)
            val pageCache = TimelineDbPageCache()
            val notifyError: (Throwable) -> Unit = { e ->
                if (e is LoginExpiredException) {
                    inAppNotification.onError(Message.LoginExpired, e)
                }
            }
            val mediator =
                if (loader is PostContextLoader) {
                    PostContextRemoteMediator(
                        loader = loader,
                        database = database,
                        preTranslationService = preTranslationService,
                        notifyError = notifyError,
                    ).also { contextMediator.value = it }
                } else {
                    TimelineRemoteMediator(
                        loader = loader,
                        database = database,
                        allowLongText = allowLongText,
                        preTranslationService = preTranslationService,
                        notifyError = notifyError,
                        rememberPosition = if (isHomeTimeline) rememberPosition else null,
                        accountKeys = sourceAccounts,
                        refreshOnInitialize = {
                            !rememberPosition &&
                                shouldRefreshTimelineOnInitialize(isHomeTimeline) {
                                    settingsRepository.appSettings.first().refreshHomeTimelineOnLaunch
                                }
                        },
                    )
                }
            val timelineMediator = mediator
            timelineMediator.prepareHomeTimeline()
            homeMediator.value = timelineMediator
            val key = timelineMediator.pagingKey
            val reading =
                if (rememberPosition) {
                    TimelineViewport(
                        database,
                        key,
                        checkNotNull(timelineTabItemId),
                        scope,
                        appScope,
                        canSave = { sourceActive.first() },
                    ).also { value ->
                        val filters = filterFlow.first()
                        val config = timelineFilterConfigFlow.first()
                        val mxgaEnabled = mxgaEnabledFlow.first()
                        val snapshot = mxgaRepository.snapshot.first()
                        value.setVisibility(listOf(filters, config, mxgaEnabled, snapshot)) { item ->
                            item.matchesKeywordFilters(filters) && item.matchesTimelineFilter(config) &&
                                (!mxgaEnabled || !item.isMxgaMatch(snapshot, platformRegistry))
                        }
                        value.restore()
                    }
                } else {
                    null
                }
            viewport.value = reading
            startupRefresh.value = reading != null && preferences.refreshHomeTimelineOnLaunch && database.pagingTimelineDao().anyPaging(key)
            var source: OffsetFromStartPagingSource<TimelinePageItem>? = null
            val pager =
                Pager(
                    config =
                        if (reading == null) {
                            offsetPagingConfig
                        } else {
                            androidx.paging.PagingConfig(
                                pageSize = offsetPagingConfig.pageSize,
                                initialLoadSize = offsetPagingConfig.initialLoadSize,
                                prefetchDistance = offsetPagingConfig.prefetchDistance,
                                enablePlaceholders = false,
                                maxSize = 200,
                            )
                        },
                    remoteMediator = mediator,
                    pagingSourceFactory = {
                        OffsetFromStartPagingSource(
                            TimelineDbPageLoader(
                                database = database,
                                pagingKey = key,
                                pageCache = pageCache,
                                windowed = reading != null,
                            ),
                            anchor = reading?.let { { it.anchor() } },
                        ).also { source = it }
                    },
                ).flow
            coroutineScope {
                if (reading != null) {
                    launch { reading.navigation.drop(1).collect { source?.invalidate() } }
                    launch {
                        database.invalidationTracker.createFlow("DbPagingTimeline", "DbStatus").collect { reading.refreshHead() }
                    }
                }
                emitAll(pager)
            }
        }

    private fun loadGap(
        gap: UiTimelineV2.Gap,
        mediator: TimelineRemoteMediator?,
        scope: CoroutineScope,
        onInserted: (List<String>) -> Unit,
    ) {
        if (mediator == null || mediator !== homeMediator.value || gapActivity.value[gap.stableItemKey] is LoadState.Loading) {
            onInserted(emptyList())
            return
        }
        gapActivity.update { it + (gap.stableItemKey to LoadState.Loading) }
        scope.launch {
            try {
                val result = mediator.loadGap(gap)
                onInserted(result.insertedKeys)
                gapActivity.update {
                    if (result.failure != null) it + (gap.stableItemKey to LoadState.Error(result.failure)) else it - gap.stableItemKey
                }
            } catch (error: CancellationException) {
                gapActivity.update { it - gap.stableItemKey }
                throw error
            } catch (error: Exception) {
                onInserted(emptyList())
                gapActivity.update { it + (gap.stableItemKey to LoadState.Error(error)) }
            }
        }
    }

    protected open suspend fun transform(data: UiTimelineV2): UiTimelineV2 = options.transform(data)

    private fun networkPager(loader: RemoteLoader<UiTimelineV2>): Flow<PagingData<UiTimelineV2>> =
        Pager(
            config = pagingConfig,
            pagingSourceFactory = {
                loader.toTimelinePagingSource()
            },
        ).flow

    @OptIn(ExperimentalCoroutinesApi::class)
    @Composable
    final override fun body(): TimelineState {
        val scope = rememberCoroutineScope()
        val items =
            remember {
                createPager(scope)
            }.collectAsLazyPagingItems()
        val contextLoadStates by remember {
            contextMediator.flatMapLatest { it?.loadStates ?: flowOf(null) }
        }.collectAsState(null)
        val reading by viewport.collectAsState()
        val target by remember(reading) { reading?.target ?: flowOf(null) }.collectAsState(null)
        val newContent by remember(reading) { reading?.hasNewContent ?: flowOf(false) }.collectAsState(false)
        val atLatest by remember(reading) { reading?.isAtLatest ?: flowOf(true) }.collectAsState(true)
        val refreshOnLaunch by startupRefresh.collectAsState()
        LaunchedEffect(reading, target, refreshOnLaunch) {
            if (reading != null && target == null && refreshOnLaunch) {
                startupRefresh.value = false
                items.refresh()
            }
        }
        DisposableEffect(reading) {
            onDispose { reading?.savePosition() }
        }
        val retry =
            remember(items) {
                {
                    contextMediator.value?.prepareRetry()
                    items.retry()
                }
            }
        val listState = items.toPagingState(contextLoadStates, retry)
        return object : TimelineState {
            override val listState = listState
            override val reading =
                reading?.let { value ->
                    object : TimelineReadingState {
                        override val restorePosition = target
                        override val hasNewContent = newContent
                        override val isAtLatest = atLatest

                        override fun viewportChanged(
                            itemKey: String,
                            offset: Double,
                        ) = value.viewportChanged(itemKey, offset)

                        override fun positionChanged(
                            itemKey: String,
                            offset: Double,
                        ) = value.positionChanged(itemKey, offset)

                        override fun savePosition() = value.savePosition()

                        override fun restorationFinished() {
                            if (value.target.value == target) value.target.value = null
                        }

                        override fun restoreCurrentPosition() {
                            if (value.target.value == null) value.target.value = value.current.value
                        }

                        override fun showLatest() = value.showLatest()
                    }
                }

            override fun refreshAsync() {
                scope.launch {
                    refresh()
                }
            }

            override suspend fun refresh() {
                listState
                    .onSuccess {
                        refreshSuspend()
                    }.onEmpty {
                        refresh()
                    }.onError {
                        onRetry()
                    }
            }
        }
    }
}

internal suspend fun shouldRefreshTimelineOnInitialize(
    isHomeTimeline: Boolean,
    refreshHomeTimelineOnLaunch: suspend () -> Boolean,
): Boolean = !isHomeTimeline || refreshHomeTimelineOnLaunch()

@Immutable
public interface TimelineState {
    public val reading: TimelineReadingState? get() = null
    public val listState: PagingState<UiTimelineV2>

    public fun refreshAsync()

    public suspend fun refresh()
}

internal fun UiTimelineV2.matchesKeywordFilters(filters: List<KeywordFilterPattern>): Boolean =
    if (this is UiTimelineV2.Gap || filters.isEmpty()) {
        true
    } else {
        !filters.any { filter ->
            searchText.orEmpty().matches(filter)
        }
    }

private fun String.matches(filter: KeywordFilterPattern): Boolean =
    if (filter.isRegex) {
        filter.regex?.containsMatchIn(this) == true
    } else {
        contains(filter.keyword, ignoreCase = true)
    }

internal fun UiTimelineV2.matchesTimelineFilter(filterConfig: TimelineFilterConfig): Boolean {
    if (filterConfig.excludedKinds.isEmpty() && filterConfig.excludedContents.isEmpty()) {
        return true
    }
    val post = asTimelinePostItem() ?: return true
    val traits = post.traits()
    return filterConfig.excludedKinds.none(traits.kinds::contains) &&
        filterConfig.excludedContents.none(traits.contents::contains)
}

internal data class TimelinePostTraits(
    val kinds: Set<TimelinePostKind>,
    val contents: Set<TimelinePostContent>,
)

internal fun UiTimelineV2.TimelinePostItem.traits(): TimelinePostTraits {
    val visiblePost = displayPost
    val kinds =
        buildSet {
            val currentUserKey = visiblePost.user?.key
            val hasParentFromOtherUser =
                currentUserKey != null &&
                    presentation.inlineParents.any { parent ->
                        parent.displayPost.user
                            ?.key
                            ?.let { it != currentUserKey } == true
                    }
            if (hasParentFromOtherUser) {
                add(TimelinePostKind.Reply)
            }
            if (presentation.repost != null) {
                add(TimelinePostKind.Repost)
            }
            if (presentation.quotes.isNotEmpty()) {
                add(TimelinePostKind.Quote)
            }
            if (
                visiblePost.references.none { it.type == ReferenceType.Reply } &&
                presentation.inlineParents.isEmpty() &&
                presentation.repost == null &&
                presentation.quotes.isEmpty()
            ) {
                add(TimelinePostKind.Original)
            }
        }
    val contents =
        buildSet {
            val hasVisualMedia =
                visiblePost.images.any {
                    it is UiMedia.Image || it is UiMedia.Gif || it is UiMedia.Ugoira ||
                        it is UiMedia.Video
                }
            if (visiblePost.content.original.raw
                    .isNotBlank() && !hasVisualMedia
            ) {
                add(TimelinePostContent.Text)
            }
            if (visiblePost.images.any { it is UiMedia.Image || it is UiMedia.Gif || it is UiMedia.Ugoira }) {
                add(TimelinePostContent.Image)
            }
            if (visiblePost.images.any { it is UiMedia.Video }) {
                add(TimelinePostContent.Video)
            }
            if (visiblePost.isTimelineFilterEmpty()) {
                add(TimelinePostContent.Other)
            }
        }
    return TimelinePostTraits(
        kinds = kinds,
        contents = contents,
    )
}

private fun UiTimelineV2.Post.isTimelineFilterEmpty(): Boolean =
    content.original.raw.isBlank() &&
        contentWarning?.original?.raw.isNullOrBlank() &&
        images.isEmpty() &&
        poll == null &&
        card == null

internal fun observeTimelineFilterConfig(
    settingsRepository: SettingsRepository,
    timelineTabItemId: String?,
): Flow<TimelineFilterConfig> =
    if (timelineTabItemId == null) {
        flowOf(TimelineFilterConfig())
    } else {
        settingsRepository
            .homeTimelineTab(timelineTabItemId)
            .map { it?.filterConfig ?: TimelineFilterConfig() }
            .distinctUntilChanged()
    }
