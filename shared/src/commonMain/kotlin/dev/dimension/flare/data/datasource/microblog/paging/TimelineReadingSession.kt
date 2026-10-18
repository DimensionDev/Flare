package dev.dimension.flare.data.datasource.microblog.paging

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.Pager
import androidx.paging.PagingState
import dev.dimension.flare.common.BaseRemoteMediator
import dev.dimension.flare.common.PlatformDispatchers
import dev.dimension.flare.common.SnowflakeIdGenerator
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.connect
import dev.dimension.flare.data.database.cache.dao.DbTimelinePageIdentity
import dev.dimension.flare.data.database.cache.mapper.saveToDatabase
import dev.dimension.flare.data.database.cache.model.DbPagingKey
import dev.dimension.flare.data.database.cache.model.DbPagingTimeline
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.database.cache.model.DbTimelineReadingCursor
import dev.dimension.flare.data.database.cache.model.DbTimelineReadingSegment
import dev.dimension.flare.data.database.cache.model.DbTimelineReadingSnapshot
import dev.dimension.flare.data.database.cache.model.TranslationDisplayOptions
import dev.dimension.flare.data.datasource.microblog.MixedRemoteMediator
import dev.dimension.flare.data.datasource.microblog.offsetPagingConfig
import dev.dimension.flare.data.repository.TimelineReadingRepository
import dev.dimension.flare.data.translation.PreTranslationService
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.TimelineReadingGap
import dev.dimension.flare.ui.model.TimelineReadingPosition
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.render.toUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal data class ReadingSessionState(
    val position: TimelineReadingPosition? = null,
    val hasNewContent: Boolean = false,
    val refreshing: Boolean = false,
    val interactionGeneration: Long = 0,
)

internal data class ReadingViewport(
    val itemKey: String?,
    val offset: Double,
    val atTop: Boolean,
    val interacting: Boolean,
)

private typealias ReadingCursorState = Map<String, Pair<DbPagingKey?, List<String>>>

@OptIn(ExperimentalPagingApi::class)
internal class TimelineReadingSession(
    val record: DbTimelineReadingSnapshot,
    private val loader: RemoteLoader<UiTimelineV2>,
    private val database: CacheDatabase,
    private val repository: TimelineReadingRepository,
    private val scope: CoroutineScope,
    private val preTranslationService: PreTranslationService,
    private val allowLongText: Boolean,
    private val refreshOnLaunch: suspend () -> Boolean,
    private val isVisible: suspend (UiTimelineV2) -> Boolean,
    private val notifyError: (Throwable) -> Unit,
) : BaseRemoteMediator<OffsetFromStartPagingKey, TimelinePageItem>() {
    var activeKey = record.activeKey
        private set
    private var pendingKey = record.pendingKey
    val state = MutableStateFlow(ReadingSessionState(hasNewContent = record.hasNewContent))

    // Keep the atomic generation guard internal; gestures alone do not change the UI.
    val presentation = state.map { it.copy(interactionGeneration = 0) }.distinctUntilChanged()
    private val viewport = MutableStateFlow(ReadingViewport(null, 0.0, atTop = false, interacting = false))

    // Paging access hints include prefetch and transient cells during native layout.
    // They must not move the window away from the post the reader actually sees.
    internal val refreshAnchor: String?
        get() = state.value.position?.itemKey ?: viewport.value.itemKey
    private val mutex = Mutex()
    private val closed = MutableStateFlow(false)
    private var checkpoint: Job? = null
    private var startupPending = false
    private var cursorIndexReady = false
    private val cursorStates = mutableMapOf<String, ReadingCursorState>()
    private val equivalentCursors = mutableMapOf<ReadingCursorState, MutableSet<String>>()
    private val dirtyCursors = mutableSetOf<String>()
    private var active = mediator(activeKey)
    private val pagingGeneration = MutableStateFlow(0)
    val gapGeneration = MutableStateFlow(0)
    private val gapLoads = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    private val appendFailure = MutableStateFlow<String?>(null)
    private val appendRetry = MutableStateFlow<String?>(null)
    private val visitedGaps = mutableSetOf<String>()
    private val continuous = (loader as? CacheableRemoteLoader<*>)?.timelineOrder == TimelineOrder.Continuous
    private val gapsAtBoundaries = continuous || (loader as? MixedRemoteMediator)?.ordersByTime == true
    private val pagingSources = MutableStateFlow<Set<TimelineReadingPagingSource>>(emptySet())
    private val boundaryVersion = MutableStateFlow(0)
    private val boundaryMutex = Mutex()
    private var boundaryCache: Triple<Int, LongRange?, List<ReadingBoundary>>? = null
    private var gapEvidenceVersion = -1
    private val gapEvidenceCache = mutableMapOf<String, Boolean>()
    private val boundaryRequests = MutableStateFlow<Pair<ReadingViewport, Int>?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val boundaryCheck =
        launchIo {
            boundaryRequests
                .mapLatest { request ->
                    request?.also { delay(200) }?.first
                }.collect { value ->
                    // A new layout may replace the debounce, but must not cancel a page already loading.
                    if (value != null) runSafely { updateReadingBoundary(value) }
                }
        }
    private val boundaryInvalidation =
        launchIo {
            database.invalidationTracker
                .createFlow(
                    "DbPagingTimeline",
                    "DbPagingKey",
                    "DbStatus",
                    "timeline_item_presentation_reference",
                    "DbTimelineReadingSegment",
                    "DbTimelineReadingCursor",
                ).collect { boundaryVersion.update { it + 1 } }
        }

    private data class ReadingBoundary(
        val segment: DbTimelineReadingSegment,
        val row: DbPagingTimeline,
        val offset: Int,
        val after: Boolean,
    )

    private suspend fun runSafely(block: suspend () -> Unit) {
        try {
            block()
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            notifyError(error)
        }
    }

    private fun launchIo(block: suspend () -> Unit) = scope.launch(PlatformDispatchers.IO) { runSafely(block) }

    internal fun registerPagingSource(source: TimelineReadingPagingSource) {
        pagingSources.update { it + source }
        source.registerInvalidatedCallback { pagingSources.update { it - source } }
        if (closed.value) source.invalidate()
    }

    private fun requestBoundaryCheck() {
        val value = viewport.value
        boundaryRequests.value =
            if (!closed.value && !value.interacting && value.itemKey != null && state.value.position == null) {
                value.copy(offset = 0.0) to gapGeneration.value
            } else {
                null
            }
    }

    private fun contentChanged() {
        boundaryVersion.update { it + 1 }
        gapGeneration.update { it + 1 }
        requestBoundaryCheck()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val pagingData =
        pagingGeneration.flatMapLatest {
            Pager(
                config = offsetPagingConfig,
                remoteMediator = this,
                pagingSourceFactory = { TimelineReadingPagingSource(database, this) },
            ).flow
        }

    private suspend fun valid(): Boolean = !closed.value && repository.current(record) != null

    private fun scopedLoader(
        key: String,
        retryFailed: Boolean = false,
        refreshCache: MixedRemoteMediator.RefreshCache? = null,
        gapBoundarySortId: Long? = null,
    ): CacheableRemoteLoader<UiTimelineV2> =
        if (loader is MixedRemoteMediator) {
            loader.forReadingSession(
                key,
                readingHeadKey = activeKey.takeIf { loader.ordersByTime },
                retryFailed = retryFailed,
                refreshCache = refreshCache,
                readingGap = gapBoundarySortId?.takeIf { loader.ordersByTime }?.let { MixedRemoteMediator.ReadingGap(it, isVisible) },
                isValid = ::valid,
            )
        } else {
            object : CacheableRemoteLoader<UiTimelineV2>, SortIdProvider {
                override val pagingKey = key
                override val timelineOrder = (loader as? CacheableRemoteLoader<*>)?.timelineOrder ?: TimelineOrder.Ranked
                override val supportPrepend = (loader as? CacheableRemoteLoader<*>)?.supportPrepend == true
                override val collapseReplyChains = (loader as? CacheableRemoteLoader<*>)?.collapseReplyChains != false

                override suspend fun load(
                    pageSize: Int,
                    request: PagingRequest,
                ) = loader.load(pageSize, request)

                override suspend fun sortId(data: UiTimelineV2) = (loader as? SortIdProvider)?.sortId(data)
            }
        }

    private fun mediator(
        key: String,
        retryFailed: Boolean = false,
        refreshCache: MixedRemoteMediator.RefreshCache? = null,
        gapBoundarySortId: Long? = null,
    ) = TimelineRemoteMediator(
        scopedLoader(key, retryFailed, refreshCache, gapBoundarySortId),
        database,
        allowLongText,
        notifyError,
        preTranslationService,
        refreshOnInitialize = { false },
        isRequestValid = ::valid,
        preservePrependSortIds = true,
    )

    suspend fun prepare() {
        val dao = database.pagingTimelineDao()
        val saved =
            record.anchorId?.let { dao.readingItem(activeKey, it) }
                ?: record.anchorSortId?.let { dao.nearestReadingItem(activeKey, it) }
                ?: dao.getTimelinePageIdentities(activeKey, 0, 1).firstOrNull()?.let {
                    dao.readingItem(activeKey, "${activeKey}_${it.statusId}")
                }
        val start = saved?.let { dao.readingItemOffset(activeKey, it.sortId) } ?: 0
        val anchor = visibleRow(activeKey, start) ?: visibleRow(activeKey, 0)
        startupPending = dao.getPagingKey(activeKey) != null
        if (anchor != null) {
            requestPosition(
                "${activeKey}_${anchor.timeline.statusId}",
                if (record.anchorId ==
                    "${activeKey}_${anchor.timeline.statusId}"
                ) {
                    record.offset
                } else {
                    0.0
                },
            )
        } else {
            finishStartup()
        }
    }

    private suspend fun visibleRow(
        key: String,
        start: Int,
    ): DbPagingTimelineWithStatus? {
        var offset = start
        while (true) {
            val rows = database.pagingTimelineDao().getTimelinePage(key, offset, 40)
            rows.firstOrNull { isVisible(it.asUi(key)) }?.let { return it }
            if (rows.size < 40) return null
            offset += rows.size
        }
    }

    private fun DbPagingTimelineWithStatus.asUi(key: String) =
        TimelinePagingMapper.toUi(this, key, TranslationDisplayOptions(false, false, ""))

    override suspend fun initialize(): InitializeAction =
        if (database.pagingTimelineDao().getPagingKey(activeKey) != null) {
            InitializeAction.SKIP_INITIAL_REFRESH
        } else {
            InitializeAction.LAUNCH_INITIAL_REFRESH
        }

    override suspend fun doLoad(
        loadType: LoadType,
        state: PagingState<OffsetFromStartPagingKey, TimelinePageItem>,
    ): MediatorResult {
        if (!valid()) return MediatorResult.Success(true)
        return when (loadType) {
            LoadType.PREPEND -> {
                MediatorResult.Success(true)
            }

            LoadType.REFRESH -> {
                refresh(automatic = false, initial = true)
                MediatorResult.Success(false)
            }

            LoadType.APPEND -> {
                if (!continuous) {
                    var retryFailed = false
                    val cursor =
                        withContext(PlatformDispatchers.IO) {
                            mutex.withLock {
                                val retryKey = appendRetry.value
                                appendRetry.value = null
                                retryFailed = retryKey != null
                                val dao = database.timelineReadingSessionDao()
                                retryKey?.let { dao.cursor(it) } ?: dao.nextCursor(record.key)
                            }
                        } ?: return MediatorResult.Success(true)
                    val error = loadGap(cursor.key, retryFailed = retryFailed)
                    appendFailure.value = cursor.key.takeIf { error != null }
                    return if (error != null) {
                        MediatorResult.Error(error)
                    } else {
                        MediatorResult.Success(database.timelineReadingSessionDao().nextCursor(record.key) == null)
                    }
                }
                withContext(PlatformDispatchers.IO) {
                    mutex.withLock {
                        val cursor =
                            database.pagingTimelineDao().getPagingKey(activeKey)?.nextKey
                                ?: return@withLock MediatorResult.Success(true)
                        val request = PagingRequest.Append(cursor)
                        val response = active.load(state.config.pageSize, request)
                        database.connect {
                            if (!valid()) return@connect
                            if (response.nextKey == cursor) {
                                val existing = existingIds(response.data)
                                check(response.data.any { it.timeline.statusId !in existing }) {
                                    "Timeline pagination did not advance"
                                }
                            }
                            val rows =
                                if (continuous) {
                                    insertContinuousRows(response.data, null)
                                } else {
                                    insertSegmentRows(response.data, null)
                                }
                            active.saveResponse(request, response.copy(data = rows))
                        }
                        contentChanged()
                        MediatorResult.Success(response.nextKey == null)
                    }
                }
            }
        }
    }

    suspend fun resolveMissingPosition() {
        val position = state.value.position ?: return
        val dao = database.pagingTimelineDao()
        val row = dao.readingItem(activeKey, position.itemKey)
        val offset = row?.let { dao.readingItemOffset(activeKey, it.sortId) } ?: 0
        val candidate = visibleRow(activeKey, offset) ?: visibleRow(activeKey, 0)
        if (state.value.position != position) return
        if (candidate == null) {
            cancelRestoration()
        } else if ("${activeKey}_${candidate.timeline.statusId}" != position.itemKey) {
            val replacement =
                TimelineReadingPosition(
                    "${activeKey}_${candidate.timeline.statusId}",
                    0.0,
                    dev.dimension.flare.common.SnowflakeIdGenerator
                        .nextId(),
                    position.latest,
                )
            state.update { if (it.position == position) it.copy(position = replacement) else it }
        }
    }

    fun savePosition() {
        if (closed.value) return
        val value = viewport.value
        if (state.value.position == null && value.itemKey != null && value.offset.isFinite()) {
            checkpoint?.cancel()
            repository.savePosition(record, activeKey, value.itemKey, value.offset)
        }
    }

    suspend fun close() {
        closed.value = true
        state.update { it.copy(interactionGeneration = it.interactionGeneration + 1) }
        checkpoint?.cancel()
        boundaryCheck.cancel()
        boundaryInvalidation.cancel()
        pagingSources.value.forEach { it.invalidate() }
        val value = viewport.value
        try {
            if (state.value.position == null && value.itemKey != null && value.offset.isFinite()) {
                repository.checkpoint(record, activeKey, value.itemKey, value.offset)
            }
        } finally {
            repository.release(record)
        }
    }

    fun updateViewport(value: ReadingViewport) {
        if (closed.value) return
        if (value.itemKey?.startsWith("reading-gap:") == true) return
        val previous = viewport.value
        viewport.value = value
        if (value.interacting && (!previous.interacting || state.value.position != null)) cancelRestoration()
        if (value.atTop && value.itemKey != null && state.value.hasNewContent &&
            (!previous.atTop || previous.itemKey != value.itemKey || previous.interacting != value.interacting)
        ) {
            // Reaching the head consumes the notice even before the gesture or momentum ends.
            launchIo {
                mutex.withLock {
                    if (valid()) dismissReadNoticeAtTop(value)
                }
            }
        }
        requestBoundaryCheck()
        if (value.interacting && !previous.interacting) launchIo { repository.selectTab(record.tabId) }
        checkpoint?.cancel()
        if (!value.interacting && value.itemKey != null && state.value.position == null && value.offset.isFinite()) {
            val version = repository.nextPositionVersion(record)
            val key = activeKey
            checkpoint =
                launchIo {
                    delay(200)
                    repository.checkpoint(record, key, value.itemKey, value.offset, version)
                }
        }
    }

    fun positionRestored(id: Long) {
        if (state.value.position?.requestId != id) return
        state.update { if (it.position?.requestId == id) it.copy(position = null) else it }
        finishStartup()
    }

    fun cancelRestoration() {
        state.update { it.copy(position = null, interactionGeneration = it.interactionGeneration + 1) }
        finishStartup()
    }

    private fun finishStartup() {
        if (!startupPending) return
        startupPending = false
        scope.launch {
            if (refreshOnLaunch()) {
                try {
                    refresh(automatic = false, initial = true)
                } catch (error: kotlinx.coroutines.CancellationException) {
                    throw error
                } catch (_: Exception) {
                    // refresh already reported the failure; keep the pager alive for manual retry.
                }
            }
        }
    }

    fun prepareRetry() {
        appendRetry.value = appendFailure.value
    }

    suspend fun refresh(
        automatic: Boolean,
        initial: Boolean = false,
    ) = withContext(PlatformDispatchers.IO) {
        mutex.withLock {
            if (!valid()) return@withLock
            val generation = state.value.interactionGeneration
            val position = viewport.value
            state.update { it.copy(refreshing = true) }
            try {
                val paused =
                    if ((loader as? MixedRemoteMediator)?.ordersByTime == true) {
                        database.timelineReadingSessionDao().pausedCursors(record.key)
                    } else {
                        emptyList()
                    }
                val unknownHeads =
                    if (paused.isEmpty()) {
                        emptyList()
                    } else {
                        paused.filter {
                            (scopedLoader(it.key) as MixedRemoteMediator).canRecoverHeadOnRefresh()
                        }
                    }
                database.connect {
                    val latest = repository.current(record) ?: return@connect
                    if ((loader as? MixedRemoteMediator)?.ordersByTime == true) {
                        database.timelineReadingSessionDao().retrySourcesOnNextLoad(record.key)
                        if (paused.isNotEmpty()) database.timelineReadingSessionDao().resumeSegments(record.key)
                    }
                    repository.saveSnapshot(record, latest.copy(pendingReady = false))
                    invalidateCursorState(pendingKey)
                    repository.deleteData(pendingKey)
                }
                if (paused.isNotEmpty()) {
                    gapLoads.update { it.filterValues { loading -> loading } }
                    visitedGaps.clear()
                    contentChanged()
                }
                val refreshCache = if (unknownHeads.isEmpty()) null else MixedRemoteMediator.RefreshCache()
                // Only unknown heads are part of refresh; historical gap pages retain their normal per-load budget.
                for (cursor in unknownHeads) loadGapLocked(cursor.key, retryFailed = true, refreshCache)
                val pending =
                    mediator(
                        pendingKey,
                        refreshCache = refreshCache,
                        gapBoundarySortId =
                            if ((loader as? MixedRemoteMediator)?.ordersByTime ==
                                true
                            ) {
                                database.pagingTimelineDao().readingHead(activeKey)?.sortId
                            } else {
                                null
                            },
                    )
                val response = pending.load(40, PagingRequest.Refresh)
                if (!valid()) return@withLock
                pending.saveResponse(PagingRequest.Refresh, response)
                val hasVisibleRows = response.data.any { isVisible(it.asUi(pendingKey)) }
                updatePendingVisibility(hasVisibleRows)
                val currentIds = existingIds(response.data)
                val oldHead = visibleRow(activeKey, 0)?.timeline?.statusId
                val hasNewRows = response.data.any { it.timeline.statusId !in currentIds }
                val newVisible = response.data.any { it.timeline.statusId !in currentIds && isVisible(it.asUi(pendingKey)) }
                if (database.pagingTimelineDao().getTimelinePageIdentities(activeKey, 0, 1).isEmpty()) {
                    publishInitial(response)
                } else if (hasNewRows || (!continuous && response.nextKey != null)) {
                    val bridge = if (!initial) continuousNewPage(response) else null
                    val followLatest =
                        bridge != null && automatic && position.atTop && !position.interacting && state.value.position == null &&
                            position.itemKey != null &&
                            position.itemKey == visibleRow(activeKey, 0)?.timeline?.let { "${activeKey}_${it.statusId}" }
                    val nextPendingKey = if (bridge == null) "${record.key}segment/${Uuid.random()}/" else pendingKey
                    currentCoroutineContext().ensureActive()
                    withContext(NonCancellable) {
                        val attached =
                            database.connect {
                                if (!valid()) return@connect false
                                if (bridge != null) {
                                    val rows =
                                        insertContinuousRows(
                                            bridge.second.data,
                                            database.pagingTimelineDao().readingHead(activeKey)?.statusId,
                                        )
                                    active.saveResponse(bridge.first, bridge.second.copy(data = rows))
                                    // The forward probe can be shorter than the fetched refresh page.
                                    attachSegment(response)
                                } else {
                                    attachSegment(response)
                                }
                                val latest = repository.current(record) ?: return@connect false
                                repository.saveSnapshot(
                                    record,
                                    latest.copy(
                                        hasNewContent = latest.hasNewContent || newVisible,
                                        pendingReady = false,
                                        pendingKey = nextPendingKey,
                                        newContentBoundaryId = latest.newContentBoundaryId ?: oldHead,
                                    ),
                                )
                                true
                            }
                        if (!attached) return@withContext
                        pendingKey = nextPendingKey
                        state.update { it.copy(hasNewContent = it.hasNewContent || newVisible) }
                        contentChanged()
                    }
                    if (followLatest && state.value.interactionGeneration == generation &&
                        !viewport.value.interacting && viewport.value.itemKey == position.itemKey &&
                        kotlin.math.abs(viewport.value.offset - position.offset) < 1.0
                    ) {
                        showLatestLocked(generation)
                    } else {
                        dismissReadNotice()
                    }
                } else {
                    database.connect {
                        if (valid()) repository.deleteData(pendingKey)
                    }
                    dismissReadNotice()
                }
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                notifyError(error)
                if (initial && !database.pagingTimelineDao().anyPaging(activeKey)) throw error
            } finally {
                state.update { it.copy(refreshing = false) }
            }
        }
    }

    private suspend fun updatePendingVisibility(hasVisibleRows: Boolean) {
        currentCoroutineContext().ensureActive()
        withContext(NonCancellable) {
            val snapshot =
                database.connect {
                    val latest = repository.current(record) ?: return@connect null
                    latest
                        .copy(pendingReady = hasVisibleRows)
                        .also { repository.saveSnapshot(record, it) }
                } ?: return@withContext
            state.update { it.copy(hasNewContent = snapshot.hasNewContent) }
        }
    }

    // Only a loader's forward cursor can prove continuity; overlapping ranked pages cannot.
    private suspend fun continuousNewPage(
        latest: PagingResult<DbPagingTimelineWithStatus>,
    ): Pair<PagingRequest.Prepend, PagingResult<DbPagingTimelineWithStatus>>? {
        if (!continuous || (loader as? CacheableRemoteLoader<*>)?.supportPrepend != true) return null
        val cursor = database.pagingTimelineDao().getPagingKey(activeKey)?.prevKey ?: return null
        val request = PagingRequest.Prepend(cursor)
        val forward =
            try {
                active.load(40, request)
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                notifyError(error)
                return null
            }
        if (forward.data.isEmpty() || forward.data
                .first()
                .timeline.statusId !=
            latest.data
                .firstOrNull()
                ?.timeline
                ?.statusId
        ) {
            return null
        }
        return request to forward
    }

    suspend fun showLatest() =
        withContext(PlatformDispatchers.IO) {
            val generation = state.value.interactionGeneration
            mutex.withLock { showLatestLocked(generation) }
        }

    private suspend fun showLatestLocked(generation: Long) {
        if (!valid() || state.value.interactionGeneration != generation || !state.value.hasNewContent || viewport.value.interacting) return
        val first = visibleRow(activeKey, 0)?.timeline
        val latest = repository.current(record) ?: return
        val boundary = latest.newContentBoundaryId?.let { database.pagingTimelineDao().readingItem(activeKey, "${activeKey}_$it") }
        val hasNew = first != null && (boundary == null || first.sortId < boundary.sortId)
        if (state.value.interactionGeneration != generation || !valid()) return
        if (hasNew && state.value.interactionGeneration == generation) {
            if (requestPosition("${activeKey}_${first.statusId}", 0.0, latest = true, generation = generation) == null) return
        }
        dismissReadNotice(force = true)
    }

    private suspend fun dismissReadNotice(force: Boolean = false) {
        val latest = repository.current(record) ?: return
        if (!latest.hasNewContent) return
        val first = visibleRow(activeKey, 0)?.timeline
        val boundary = latest.newContentBoundaryId?.let { database.pagingTimelineDao().readingItem(activeKey, "${activeKey}_$it") }
        if (force || first == null || (boundary != null && first.sortId >= boundary.sortId)) {
            withContext(NonCancellable) {
                database.connect {
                    repository
                        .current(
                            record,
                        )?.let { repository.saveSnapshot(record, it.copy(hasNewContent = false, newContentBoundaryId = null)) }
                }
                state.update { it.copy(hasNewContent = false) }
            }
        }
    }

    private suspend fun dismissReadNoticeAtTop(value: ReadingViewport) {
        if (value.atTop && value.itemKey != null && state.value.position == null && state.value.hasNewContent &&
            value.itemKey == visibleRow(activeKey, 0)?.timeline?.let { "${activeKey}_${it.statusId}" }
        ) {
            dismissReadNotice(force = true)
        }
    }

    private suspend fun publishInitial(response: PagingResult<DbPagingTimelineWithStatus>) {
        val first = visibleRow(pendingKey, 0)?.timeline
        currentCoroutineContext().ensureActive()
        // Commit the first page and its in-memory namespace together, even if refresh is cancelled.
        withContext(NonCancellable) {
            val published =
                database.connect {
                    val latest = repository.current(record) ?: return@connect false
                    val dao = database.pagingTimelineDao()
                    if ((loader as? MixedRemoteMediator)?.ordersByTime != true) {
                        dao.insertAll(dao.getByPagingKey(pendingKey).mapIndexed { index, row -> row.copy(sortId = index * 1_000_000L) })
                    }
                    reclaimPublishedStaging(pendingKey, pendingKey)
                    if (!continuous) rememberRankedCursor(pendingKey, response.data.map { it.timeline.statusId }, response.canContinue)
                    repository.saveSnapshot(
                        record,
                        latest.copy(
                            activeKey = pendingKey,
                            pendingKey = activeKey,
                            anchorId = first?.let { "${pendingKey}_${it.statusId}" },
                            anchorSortId = first?.let { dao.readingItem(pendingKey, "${pendingKey}_${it.statusId}")?.sortId },
                            offset = 0.0,
                            hasNewContent = false,
                            pendingReady = false,
                            newContentBoundaryId = null,
                        ),
                    )
                    invalidateCursorState(activeKey)
                    repository.deleteData(activeKey)
                    true
                }
            if (!published) return@withContext
            val previousKey = activeKey
            activeKey = pendingKey
            pendingKey = previousKey
            active = mediator(activeKey)
            pagingGeneration.update { it + 1 }
            state.update { it.copy(hasNewContent = false) }
        }
    }

    private suspend fun existingIds(data: List<DbPagingTimelineWithStatus>): Set<String> =
        data
            .map { it.timeline.statusId }
            .distinct()
            .chunked(500)
            .flatMap {
                database.pagingTimelineDao().existingReadingStatusIds(activeKey, it)
            }.toSet()

    private suspend fun existingRows(data: List<DbPagingTimelineWithStatus>): Map<String, DbPagingTimeline> =
        data
            .map { it.timeline.statusId }
            .distinct()
            .chunked(500)
            .flatMap {
                database.pagingTimelineDao().getByPagingKeyAndStatusIds(activeKey, it)
            }.associateBy { it.statusId }

    private suspend fun discardSegment(key: String) {
        invalidateCursorState(key)
        database.timelineReadingSessionDao().deleteSegment(key)
        repository.deleteData(key)
        visitedGaps -= key
        gapLoads.update { it - key }
    }

    private suspend fun rememberRankedCursor(
        key: String,
        ids: List<String>?,
        canContinue: Boolean = true,
    ) {
        invalidateCursorState(key)
        val dao = database.timelineReadingSessionDao()
        if (database.pagingTimelineDao().getPagingKey(key)?.nextKey == null) {
            dao.deleteCursors(key)
        } else {
            dao.saveCursor(
                DbTimelineReadingCursor(
                    key,
                    record.key,
                    dao.cursor(key)?.lastAttempt ?: SnowflakeIdGenerator.nextId(),
                    ids?.let { Json.encodeToString(it.distinct()) },
                    paused = !canContinue,
                ),
            )
        }
    }

    private suspend fun clearConsumedPage(key: String) {
        val dao = database.pagingTimelineDao()
        dao.deletePresentationReferences(key)
        dao.delete(key)
    }

    private suspend fun reclaimPublishedStaging(
        key: String,
        publishedKey: String = activeKey,
    ) {
        if (loader is MixedRemoteMediator) {
            invalidateCursorState(key)
            loader
                .forReadingSession(
                    key,
                    readingHeadKey =
                        activeKey.takeIf {
                            loader.ordersByTime
                        },
                    isValid = ::valid,
                ).reclaimPublishedStaging(publishedKey)
        }
    }

    private fun invalidateCursorState(key: String) {
        cursorStates.remove(key)?.let { previous ->
            equivalentCursors[previous]?.let { keys ->
                keys.remove(key)
                if (keys.isEmpty()) equivalentCursors.remove(previous)
            }
        }
        // Re-read after the transaction, including after rollback; never cache uncommitted state.
        dirtyCursors += key
    }

    private suspend fun cursorState(prefix: String): ReadingCursorState {
        val dao = database.pagingTimelineDao()
        val cursors = dao.pagingKeysWithPrefix(prefix).associateBy { it.pagingKey }
        return dao
            .keysWithPrefix(prefix)
            .mapNotNull { source ->
                var cursor = cursors[source]
                val identity = source.removePrefix(prefix)
                val staged = if (source == prefix) emptyList() else dao.getByPagingKey(source).map { it.statusId }
                if (source != prefix && staged.isEmpty() && cursor != null && loader is MixedRemoteMediator) {
                    cursor = loader.readingCursor(cursor) ?: return@mapNotNull null
                }
                identity to
                    Pair(
                        cursor?.copy(pagingKey = identity),
                        staged,
                    )
            }.toMap()
    }

    private suspend fun hasEquivalentCursor(key: String): Boolean {
        val dao = database.timelineReadingSessionDao()
        if (!cursorIndexReady) {
            dirtyCursors += dao.cursors(record.key).map { it.key }
            cursorIndexReady = true
        }
        dirtyCursors.toList().forEach { changed ->
            if (dao.cursor(changed) != null) {
                val value = cursorState(changed)
                cursorStates[changed] = value
                equivalentCursors.getOrPut(value) { mutableSetOf() }.add(changed)
            }
            dirtyCursors.remove(changed)
        }
        // Equal tokens can have different gap boundaries; compare source progress and staged rows too.
        return equivalentCursors[cursorState(key)]?.any { it != key } == true
    }

    private suspend fun attachSegment(response: PagingResult<DbPagingTimelineWithStatus>) {
        val dao = database.pagingTimelineDao()
        val boundary = dao.readingHead(activeKey) ?: return
        val joined = continuous && existingRows(response.data).isNotEmpty()
        if (continuous) {
            insertContinuousRows(response.data, boundary.statusId)
        } else {
            insertSegmentRows(response.data, boundary.statusId)
        }
        reclaimPublishedStaging(pendingKey)
        (scopedLoader(pendingKey) as? MixedRemoteMediator)?.let {
            it.publishReadingHeads(activeKey)
            invalidateCursorState(activeKey)
        }
        if (joined || response.nextKey == null || (!continuous && hasEquivalentCursor(pendingKey))) {
            discardSegment(pendingKey)
        } else {
            database.timelineReadingSessionDao().saveSegment(
                DbTimelineReadingSegment(
                    pendingKey,
                    record.key,
                    boundary.statusId,
                    dao.readingItem(activeKey, "${activeKey}_${boundary.statusId}")?.sortId ?: boundary.sortId,
                    failed = !response.canContinue,
                    confirmed = response.hasConfirmedGap,
                ),
            )
            if (!continuous) {
                rememberRankedCursor(pendingKey, response.data.map { it.timeline.statusId }, response.canContinue)
                clearConsumedPage(pendingKey)
            }
        }
    }

    // Keep every fetched row, including holes and rows beyond the old tail, beside its known neighbours.
    private suspend fun insertContinuousRows(
        data: List<DbPagingTimelineWithStatus>,
        beforeId: String?,
    ): List<DbPagingTimelineWithStatus> {
        val dao = database.pagingTimelineDao()
        val existing = existingRows(data)
        val additions = mutableListOf<DbPagingTimelineWithStatus>()
        val updated = mutableListOf<DbPagingTimelineWithStatus>()
        var lastKnown: String? = null
        data.distinctBy { it.timeline.statusId }.forEach { item ->
            if (item.timeline.statusId in existing) {
                updated += insertSegmentRows(additions, item.timeline.statusId)
                additions.clear()
                lastKnown = item.timeline.statusId
            } else {
                additions += item
            }
        }
        val next = lastKnown?.let { dao.readingItem(activeKey, "${activeKey}_$it") }
        val tailBoundary = if (next != null) dao.readingSuccessor(activeKey, next.sortId)?.statusId else beforeId
        updated += insertSegmentRows(additions, tailBoundary)
        updated += insertSegmentRows(data.filter { it.timeline.statusId in existing }, beforeId)
        return updated
    }

    private suspend fun rebaseAround(
        beforeId: String?,
        additions: Int,
    ) {
        val dao = database.pagingTimelineDao()
        val boundary = beforeId?.let { dao.readingItem(activeKey, "${activeKey}_$it") }
        val pivot = boundary ?: dao.readingTail(activeKey) ?: return
        var count = 32
        while (true) {
            val window =
                dao.readingRowsBefore(activeKey, pivot.sortId, count).asReversed() +
                    dao.readingRowsFrom(activeKey, pivot.sortId, count)
            val predecessor = dao.readingPredecessor(activeKey, window.first().sortId)
            val successor = dao.readingSuccessor(activeKey, window.last().sortId)
            val slots = window.size + additions + 1L
            val space = slots * 1_000_000L
            val left = predecessor?.sortId ?: ((successor?.sortId ?: 0L) - space)
            val right = successor?.sortId ?: (left + space)
            if (left >= Long.MIN_VALUE / 2 && right <= Long.MAX_VALUE / 2 && right > left && (right - left) / slots > 0) {
                val step = (right - left) / slots
                val rebased =
                    window.mapIndexed { index, row ->
                        val reserved = if (boundary != null && row.sortId >= boundary.sortId) additions else 0
                        row.copy(sortId = left + step * (index + reserved + 1L))
                    }
                dao.insertAll(rebased)
                val byId = rebased.associateBy { it.statusId }
                database.timelineReadingSessionDao().segments(record.key).forEach { segment ->
                    byId[segment.boundaryId]?.let { row ->
                        database.timelineReadingSessionDao().saveSegment(segment.copy(boundarySortId = row.sortId))
                    }
                }
                repository.current(record)?.let { snapshot ->
                    rebased.firstOrNull { "${activeKey}_${it.statusId}" == snapshot.anchorId }?.let { anchor ->
                        repository.saveSnapshot(record, snapshot.copy(anchorSortId = anchor.sortId))
                    }
                }
                return
            }
            count *= 2
        }
    }

    private suspend fun insertSegmentRows(
        data: List<DbPagingTimelineWithStatus>,
        beforeId: String?,
    ): List<DbPagingTimelineWithStatus> {
        if (data.isEmpty()) return emptyList()
        val dao = database.pagingTimelineDao()
        var existing = existingRows(data)
        val seen = existingIds(data)
        val additions = data.distinctBy { it.timeline.statusId }.filter { it.timeline.statusId !in seen }
        val orderedByTime = (loader as? MixedRemoteMediator)?.ordersByTime == true
        val sortIds =
            if (orderedByTime) {
                additions.map { requireNotNull((loader as SortIdProvider).sortId(it.asUi(activeKey))) }
            } else if (additions.isEmpty()) {
                emptyList()
            } else {
                suspend fun bounds(): Pair<Long, Long> {
                    val rightRow = beforeId?.let { dao.readingItem(activeKey, "${activeKey}_$it") }
                    val leftRow = if (rightRow != null) dao.readingPredecessor(activeKey, rightRow.sortId) else dao.readingTail(activeKey)
                    val space = (additions.size + 1L) * 1_000_000L
                    val right = rightRow?.sortId ?: ((leftRow?.sortId ?: 0L) + space)
                    val left = leftRow?.sortId ?: (right - space)
                    return left to right
                }
                var (left, right) = bounds()
                if (right <= left || (right - left) / (additions.size + 1L) <= 0 || left < Long.MIN_VALUE / 2 ||
                    right > Long.MAX_VALUE / 2
                ) {
                    rebaseAround(beforeId, additions.size)
                    existing = existingRows(data)
                    val bounds = bounds()
                    left = bounds.first
                    right = bounds.second
                }
                val step = (right - left) / (additions.size + 1L)
                additions.indices.map { right - step * (additions.size - it) }
            }
        val newSortIds = additions.mapIndexed { index, row -> row.timeline.statusId to sortIds[index] }.toMap()
        val updated =
            data.distinctBy { it.timeline.statusId }.mapNotNull { item ->
                val sortId = existing[item.timeline.statusId]?.sortId ?: newSortIds[item.timeline.statusId] ?: return@mapNotNull null
                val id = DbPagingTimeline.createId(activeKey, item.timeline.statusId)
                item.copy(
                    timeline = item.timeline.copy(pagingKey = activeKey, _id = id, sortId = sortId),
                    presentationReferences =
                        item.presentationReferences.map { reference ->
                            reference.copy(reference = reference.reference.copy(pagingKey = activeKey, timelineId = id))
                        },
                )
            }
        saveToDatabase(database, updated)
        return updated
    }

    internal suspend fun loadGap(
        key: String,
        retryFailed: Boolean = true,
    ): Exception? =
        withContext(PlatformDispatchers.IO) {
            mutex.withLock { loadGapLocked(key, retryFailed) }
        }

    private suspend fun loadGapLocked(
        key: String,
        retryFailed: Boolean,
        refreshCache: MixedRemoteMediator.RefreshCache? = null,
    ): Exception? {
        if (!valid()) return null
        val readingDao = database.timelineReadingSessionDao()
        var segment = readingDao.segment(record.key, key)
        val original = !continuous && key == activeKey
        val usesGapLoadState = !original || !gapsAtBoundaries
        if (segment == null && !original) return null
        var retrySources = retryFailed
        if (!continuous) {
            val checkpoint = readingDao.cursor(key) ?: return null
            retrySources = retrySources || checkpoint.retryFailed
            // Persist the turn before fetching: failed or infinite cursors cannot starve other batches after reopening.
            readingDao.saveCursor(checkpoint.copy(lastAttempt = SnowflakeIdGenerator.nextId()))
        }
        // The original time-mixed tail uses Paging load states and database invalidations.
        // Rebuilding its page for loading/error can drop a retry during the generation handoff.
        gapLoads.update { it + (key to true) }
        if (usesGapLoadState) gapGeneration.update { it + 1 }
        return try {
            if (loader is MixedRemoteMediator) {
                val maintained =
                    database.connect {
                        if (!valid()) return@connect false
                        // Other cursors may have published these rows since this cursor's last turn.
                        reclaimPublishedStaging(key)
                        true
                    }
                if (!maintained) return null
            }
            // Continuous gaps get two pages per visit; ranked cursors each get one page per turn.
            repeat(if (continuous) 2 else 1) {
                val dao = database.pagingTimelineDao()
                val cursor = dao.getPagingKey(key)?.nextKey ?: return@repeat
                val request = PagingRequest.Append(cursor)
                val source = if (original && !retrySources) active else mediator(key, retrySources, refreshCache, segment?.boundarySortId)
                val oldIds =
                    if (continuous) {
                        dao.getByPagingKey(key).map { it.statusId }.toSet()
                    } else {
                        readingDao.cursor(key)?.lastPageIds?.let { Json.decodeFromString<List<String>>(it).toSet() }
                    }
                val oldCursors = cursorState(key)
                val response = source.load(40, request)
                if (!valid()) return null
                currentCoroutineContext().ensureActive()
                withContext(NonCancellable) {
                    var recoveredHead = false
                    database.connect {
                        if (!valid()) return@connect
                        invalidateCursorState(key)
                        if (segment?.confirmed == false && response.data.isNotEmpty()) {
                            val existing = existingIds(response.data)
                            if (response.data.any { it.timeline.statusId !in existing && isVisible(it.asUi(activeKey)) }) {
                                val latest = repository.current(record) ?: return@connect
                                repository.saveSnapshot(
                                    record,
                                    latest.copy(
                                        hasNewContent = true,
                                        newContentBoundaryId = latest.newContentBoundaryId ?: segment?.boundaryId,
                                    ),
                                )
                                recoveredHead = true
                            }
                        }
                        val saved = if (original) response.copy(data = insertSegmentRows(response.data, null)) else response
                        source.saveResponse(request, saved)
                        if (!continuous && !original) insertSegmentRows(response.data, null)
                        reclaimPublishedStaging(key)
                        val nextCursors = cursorState(key)
                        check(
                            !response.canContinue || response.nextKey == null || oldCursors != nextCursors || oldIds == null ||
                                response.data.any { it.timeline.statusId !in oldIds },
                        ) {
                            "Timeline pagination did not advance"
                        }
                        if (original) {
                            rememberRankedCursor(key, response.data.map { it.timeline.statusId }, response.canContinue)
                        } else {
                            val current =
                                requireNotNull(segment).copy(
                                    failed = !response.canContinue,
                                    confirmed = response.hasConfirmedGap,
                                )
                            val boundary =
                                dao.readingItem(activeKey, "${activeKey}_${current.boundaryId}")
                                    ?: dao.nearestReadingItem(activeKey, current.boundarySortId)
                            val boundaryId = boundary?.statusId ?: current.boundaryId
                            val overlaps =
                                continuous &&
                                    existingRows(response.data).values.any {
                                        it.sortId >= (boundary?.sortId ?: current.boundarySortId)
                                    }
                            if (continuous) {
                                insertContinuousRows(
                                    response.data,
                                    boundaryId,
                                )
                            }
                            if (response.nextKey == null || overlaps) {
                                discardSegment(key)
                            } else if (!continuous) {
                                readingDao.saveSegment(current)
                                segment = current
                                rememberRankedCursor(key, response.data.map { it.timeline.statusId }, response.canContinue)
                                clearConsumedPage(key)
                            } else {
                                segment =
                                    current.copy(
                                        boundaryId = boundaryId,
                                        boundarySortId =
                                            dao
                                                .readingItem(
                                                    activeKey,
                                                    "${activeKey}_$boundaryId",
                                                )?.sortId ?: current.boundarySortId,
                                    )
                                readingDao.saveSegment(requireNotNull(segment))
                                val keep = response.data.map { it.timeline.statusId }.toSet()
                                val stale = dao.getByPagingKey(key).filter { it.statusId !in keep }
                                dao.deletePresentationReferences(key, stale.map { it._id })
                                dao.delete(stale)
                            }
                        }
                    }
                    boundaryVersion.update { it + 1 }
                    if (usesGapLoadState) gapGeneration.update { it + 1 }
                    if (recoveredHead) state.update { it.copy(hasNewContent = true) }
                }
                response.continuationError?.let { error ->
                    gapLoads.update { it + (key to false) }
                    return error
                }
            }
            null
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            readingDao.segment(record.key, key)?.let { readingDao.saveSegment(it.copy(failed = true)) }
            gapLoads.update { it + (key to false) }
            notifyError(error)
            error
        } finally {
            gapLoads.update { if (it[key] == true) it - key else it }
            boundaryVersion.update { it + 1 }
            if (usesGapLoadState) gapGeneration.update { it + 1 }
        }
    }

    private suspend fun readingBoundaries(window: LongRange? = null): List<ReadingBoundary> =
        boundaryMutex.withLock {
            val version = boundaryVersion.value
            boundaryCache?.takeIf { it.first == version && it.second == window }?.let { return@withLock it.third }
            if (gapEvidenceVersion != version) {
                gapEvidenceCache.clear()
                gapEvidenceVersion = version
            }
            val dao = database.pagingTimelineDao()
            val readingDao = database.timelineReadingSessionDao()
            val boundaries =
                if (gapsAtBoundaries) {
                    val segments =
                        if (window != null) {
                            // A filtered boundary can move into this page, but cannot cross a preceding visible post.
                            var offset = dao.readingItemOffset(activeKey, window.first) - 1
                            var lowerBound = Long.MIN_VALUE
                            while (offset >= 0) {
                                val start = (offset - 39).coerceAtLeast(0)
                                val previous =
                                    dao
                                        .getTimelinePage(activeKey, start, offset - start + 1)
                                        .lastOrNull { isVisible(it.asUi(activeKey)) }
                                if (previous != null) {
                                    lowerBound = previous.timeline.sortId
                                    break
                                }
                                offset = start - 1
                            }
                            readingDao.segmentsInWindow(record.key, lowerBound, window.last)
                        } else {
                            readingDao.segments(record.key).filter { it.confirmed }
                        }
                    val first = if (segments.isNotEmpty()) visibleRow(activeKey, 0)?.timeline else null
                    segments.mapNotNull { segment ->
                        val sortId =
                            if (window != null) {
                                segment.boundarySortId
                            } else {
                                (
                                    dao.readingItem(activeKey, "${activeKey}_${segment.boundaryId}")
                                        ?: dao.nearestReadingItem(activeKey, segment.boundarySortId)
                                )?.sortId ?: return@mapNotNull null
                            }
                        if (first == null || first.sortId >= sortId) return@mapNotNull null
                        // Persisted confirmation predates this page's publication and filters.
                        // Recheck only candidate boundaries, without deleting their retry cursors.
                        if (!continuous) {
                            val confirmed =
                                gapEvidenceCache.getOrPut(segment.key) {
                                    if (gapEvidenceCache.size >= 64) gapEvidenceCache.remove(gapEvidenceCache.keys.first())
                                    (scopedLoader(segment.key, gapBoundarySortId = sortId) as MixedRemoteMediator).hasVisibleReadingGap()
                                }
                            if (!confirmed) return@mapNotNull null
                        }
                        val visible =
                            visibleRow(activeKey, dao.readingItemOffset(activeKey, sortId))?.timeline
                                ?: return@mapNotNull null
                        if (window != null && visible.sortId !in window) return@mapNotNull null
                        ReadingBoundary(segment, visible, dao.readingItemOffset(activeKey, visible.sortId), after = false)
                    }
                } else {
                    val failed =
                        gapLoads.value.entries
                            .firstOrNull { !it.value }
                            ?.key
                            ?.let { readingDao.cursor(it) }
                    val cursor = failed ?: if (readingDao.hasSegments(record.key)) readingDao.nextCursor(record.key) else null
                    val tail = dao.readingTail(activeKey)
                    if (cursor != null && tail != null) {
                        val segment = DbTimelineReadingSegment(cursor.key, record.key, tail.statusId, tail.sortId)
                        var offset = dao.readingItemOffset(activeKey, tail.sortId)
                        var visibleOffset = offset
                        while (offset >= 0) {
                            val start = (offset - 39).coerceAtLeast(0)
                            val rows = dao.getTimelinePage(activeKey, start, offset - start + 1)
                            val visible = rows.indexOfLast { isVisible(it.asUi(activeKey)) }
                            if (visible >= 0) {
                                visibleOffset = start + visible
                                break
                            }
                            offset = start - 1
                        }
                        listOf(ReadingBoundary(segment, tail, visibleOffset, after = true))
                    } else {
                        emptyList()
                    }
                }
            if (boundaryVersion.value == version) boundaryCache = Triple(version, window, boundaries)
            boundaries
        }

    private suspend fun updateReadingBoundary(value: ReadingViewport) {
        val toLoad =
            mutex.withLock {
                if (!valid() || viewport.value.interacting || viewport.value.itemKey != value.itemKey) return@withLock null
                val dao = database.pagingTimelineDao()
                val row = value.itemKey?.let { dao.readingItem(activeKey, it) } ?: return@withLock null
                val offset = dao.readingItemOffset(activeKey, row.sortId)
                dismissReadNoticeAtTop(value)
                val window =
                    if (!continuous && gapsAtBoundaries) {
                        val rows = dao.getTimelinePageIdentities(activeKey, (offset - 8).coerceAtLeast(0), 17)
                        rows.firstOrNull()?.let { it.sortId..rows.last().sortId }
                    } else {
                        null
                    }
                val boundaries = readingBoundaries(window)
                if (window != null) visitedGaps.retainAll(boundaries.map { it.segment.key }.toSet())
                boundaries.filter { kotlin.math.abs(it.offset - offset) > 8 }.forEach { visitedGaps -= it.segment.key }
                boundaries
                    .firstOrNull {
                        kotlin.math.abs(it.offset - offset) <= 5 && !it.segment.failed && gapLoads.value[it.segment.key] != false &&
                            it.segment.key !in visitedGaps
                    }?.segment
                    ?.key
                    ?.also { visitedGaps += it }
            }
        if (toLoad != null) loadGap(toLoad, retryFailed = false)
    }

    suspend fun filtersChanged() =
        withContext(PlatformDispatchers.IO) {
            runSafely {
                mutex.withLock {
                    if (!valid()) return@withLock
                    dismissReadNotice()
                    contentChanged()
                }
            }
        }

    internal suspend fun withGapMarkers(items: List<TimelinePageItem>): List<TimelinePageItem> {
        if (items.isEmpty()) return items
        val window = if (!continuous && gapsAtBoundaries) items.first().sortId..items.last().sortId else null
        val boundaries = readingBoundaries(window).groupBy { it.row.statusId }
        return items.flatMap { item ->
            val matching = boundaries[item.statusId] ?: return@flatMap listOf(item)
            val markers =
                matching.map { boundary ->
                    val segment = boundary.segment
                    val loading = gapLoads.value[segment.key] == true
                    val failed = !loading && (segment.failed || gapLoads.value[segment.key] == false)
                    val kind =
                        when {
                            loading -> UiTimelineV2.Message.Type.Localized.MessageId.ReadingGapLoading
                            failed -> UiTimelineV2.Message.Type.Localized.MessageId.ReadingGapFailed
                            else -> UiTimelineV2.Message.Type.Localized.MessageId.ReadingGap
                        }
                    val marker =
                        UiTimelineV2.Message(
                            statusKey = MicroBlogKey(segment.key, "reading"),
                            icon = UiIcon.Info,
                            type = UiTimelineV2.Message.Type.Localized(kind),
                            createdAt = Instant.fromEpochMilliseconds(0).toUi(),
                            clickEvent = ClickEvent.Noop,
                            accountType = AccountType.Guest,
                            itemKey = "reading-gap:${segment.key}",
                            readingGap = TimelineReadingGap(loading, failed, true) { launchIo { loadGap(segment.key) } },
                        )
                    val pageItem =
                        TimelinePageItem(
                            DbTimelinePageIdentity(segment.key, boundary.row.sortId, marker.renderHash, 0),
                            marker,
                            emptyMap(),
                        )
                    pageItem
                }
            if (matching.first().after) listOf(item) + markers else markers + item
        }
    }

    private suspend fun requestPosition(
        key: String,
        offset: Double,
        latest: Boolean = false,
        generation: Long = state.value.interactionGeneration,
    ): TimelineReadingPosition? {
        val position =
            TimelineReadingPosition(
                key,
                offset,
                dev.dimension.flare.common.SnowflakeIdGenerator
                    .nextId(),
                latest,
            )
        return state
            .updateAndGet {
                if (it.interactionGeneration == generation && !viewport.value.interacting) it.copy(position = position) else it
            }.position
            ?.takeIf { it == position }
    }
}
