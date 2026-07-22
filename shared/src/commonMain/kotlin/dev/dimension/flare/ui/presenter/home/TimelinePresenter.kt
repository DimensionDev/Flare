package dev.dimension.flare.ui.presenter.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.paging.ExperimentalPagingApi
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
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.database.cache.model.TranslationDisplayOptions
import dev.dimension.flare.data.datasource.microblog.offsetPagingConfig
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.NotSupportRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.OffsetFromStartPagingSource
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.TimelineDbPageCache
import dev.dimension.flare.data.datasource.microblog.paging.TimelineDbPageLoader
import dev.dimension.flare.data.datasource.microblog.paging.TimelinePagingMapper
import dev.dimension.flare.data.datasource.microblog.paging.TimelineRemoteMediator
import dev.dimension.flare.data.datasource.microblog.paging.notSupported
import dev.dimension.flare.data.datasource.microblog.paging.toPagingSource
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
import dev.dimension.flare.model.ReferenceType
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.asTimelinePostItem
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.web.shared.WebPresenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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

    private val localFilterRepository: LocalFilterRepository by koinInject()
    private val inAppNotification: InAppNotification by koinInject()

    private val filterFlow: Flow<List<KeywordFilterPattern>> by lazy {
        localFilterRepository.getFlow(forTimeline = true)
    }

    private val timelineTabItemId: String?
    private val isHomeTimeline: Boolean

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
            .flatMapLatest { remoteLoader ->
                when (remoteLoader) {
                    is NotSupportRemoteLoader<UiTimelineV2> -> {
                        PagingData.emptyFlow(isError = false)
                    }

                    is CacheableRemoteLoader<UiTimelineV2> -> {
                        cachePager(
                            loader = remoteLoader,
                        ).cachedIn(scope).flatMapLatest { pagingData ->
                            translationSettingsFlow
                                .map { translationDisplayOptions ->
                                    withContext(PlatformDispatchers.IO) {
                                        pagingData.map { item ->
                                            TimelinePagingMapper.toUi(
                                                item = item,
                                                pagingKey = remoteLoader.pagingKey,
                                                translationDisplayOptions = translationDisplayOptions,
                                            )
                                        }
                                    }
                                }
                        }
                    }

                    else -> {
                        networkPager(
                            loader = remoteLoader,
                        ).cachedIn(scope)
                    }
                }.flatMapLatest { pager ->
                    combine(
                        filterFlow,
                        timelineFilterConfigFlow,
                        mxgaEnabledFlow,
                        mxgaRepository.snapshot,
                    ) { filterList, timelineFilterConfig, mxgaEnabled, mxgaSnapshot ->
                        pager
                            .filter { item ->
                                item.matchesKeywordFilters(filterList) &&
                                    item.matchesTimelineFilter(timelineFilterConfig) &&
                                    (!mxgaEnabled || !item.isMxgaMatch(mxgaSnapshot))
                            }.map {
                                transform(it)
                            }
                    }
                }
            }.catch {
                emitAll(PagingData.emptyFlow(isError = true))
            }

    private fun cachePager(loader: CacheableRemoteLoader<UiTimelineV2>): Flow<PagingData<DbPagingTimelineWithStatus>> =
        run {
            val allowLongText = allowLongTextTranslationDisplay(loader)
            val pageCache = TimelineDbPageCache()
            Pager(
                config = offsetPagingConfig,
                remoteMediator =
                    TimelineRemoteMediator(
                        loader = loader,
                        database = database,
                        allowLongText = allowLongText,
                        preTranslationService = preTranslationService,
                        notifyError = { e ->
                            if (e is LoginExpiredException) {
                                inAppNotification.onError(Message.LoginExpired, e)
                            }
                        },
                        refreshOnInitialize = {
                            shouldRefreshTimelineOnInitialize(isHomeTimeline) {
                                settingsRepository.appSettings.first().refreshHomeTimelineOnLaunch
                            }
                        },
                    ),
                pagingSourceFactory = {
                    OffsetFromStartPagingSource(
                        TimelineDbPageLoader(
                            database = database,
                            pagingKey = loader.pagingKey,
                            pageCache = pageCache,
                        ),
                    )
                },
            ).flow
        }

    protected open suspend fun transform(data: UiTimelineV2): UiTimelineV2 = options.transform(data)

    private fun networkPager(loader: RemoteLoader<UiTimelineV2>): Flow<PagingData<UiTimelineV2>> =
        Pager(
            config = pagingConfig,
            pagingSourceFactory = {
                loader.toPagingSource()
            },
        ).flow

    @Composable
    final override fun body(): TimelineState {
        val scope = rememberCoroutineScope()
        val listState =
            remember {
                createPager(scope)
            }.collectAsLazyPagingItems()
                .toPagingState()
        return object : TimelineState {
            override val listState = listState

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
    public val listState: PagingState<UiTimelineV2>

    public fun refreshAsync()

    public suspend fun refresh()
}

internal fun UiTimelineV2.matchesKeywordFilters(filters: List<KeywordFilterPattern>): Boolean =
    if (filters.isEmpty()) {
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
                        parent.user?.key?.let { it != currentUserKey } == true
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
            val hasVisualMedia = visiblePost.images.any { it is UiMedia.Image || it is UiMedia.Gif || it is UiMedia.Video }
            if (visiblePost.content.original.raw
                    .isNotBlank() && !hasVisualMedia
            ) {
                add(TimelinePostContent.Text)
            }
            if (visiblePost.images.any { it is UiMedia.Image || it is UiMedia.Gif }) {
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
