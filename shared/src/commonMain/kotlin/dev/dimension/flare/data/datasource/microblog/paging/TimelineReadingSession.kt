package dev.dimension.flare.data.datasource.microblog.paging

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.Pager
import androidx.paging.PagingState
import dev.dimension.flare.common.BaseRemoteMediator
import dev.dimension.flare.common.PlatformDispatchers
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.connect
import dev.dimension.flare.data.database.cache.dao.DbTimelinePageIdentity
import dev.dimension.flare.data.database.cache.mapper.saveToDatabase
import dev.dimension.flare.data.database.cache.model.DbPagingTimeline
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
    private val viewport = MutableStateFlow(ReadingViewport(null, 0.0, atTop = false, interacting = false))
    private val mutex = Mutex()
    private val closed = MutableStateFlow(false)
    private var checkpoint: Job? = null
    private var startupPending = false
    private var active = mediator(activeKey)
    private val pagingGeneration = MutableStateFlow(0)
    val gapGeneration = MutableStateFlow(0)
    private val gapLoads = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    private val visitedGaps = mutableSetOf<String>()
    private val continuous = (loader as? CacheableRemoteLoader<*>)?.timelineOrder == TimelineOrder.Continuous
    private val pagingSources = MutableStateFlow<Set<TimelineReadingPagingSource>>(emptySet())
    private val boundaryVersion = MutableStateFlow(0)
    private val boundaryMutex = Mutex()
    private var boundaryCache: Pair<Int, List<ReadingBoundary>>? = null
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
                .createFlow("DbPagingTimeline", "DbStatus", "timeline_item_presentation_reference", "DbTimelineReadingSegment")
                .collect { boundaryVersion.update { it + 1 } }
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

    private fun scopedLoader(key: String): CacheableRemoteLoader<UiTimelineV2> =
        if (loader is MixedRemoteMediator) {
            loader.forReadingSession(key, ::valid)
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

    private fun mediator(key: String) =
        TimelineRemoteMediator(
            scopedLoader(key),
            database,
            allowLongText,
            notifyError,
            preTranslationService,
            refreshOnInitialize = { false },
            isRequestValid = ::valid,
        )

    suspend fun prepare() {
        database.connect {
            if (valid()) {
                database
                    .timelineReadingSessionDao()
                    .segments(
                        record.key,
                    ).filter { it.exhausted }
                    .forEach { discardSegment(it.key) }
            }
        }
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
                val continuation =
                    if (!continuous) {
                        database
                            .timelineReadingSessionDao()
                            .segments(
                                record.key,
                            ).lastOrNull { !it.exhausted }
                    } else {
                        null
                    }
                if (continuation != null) {
                    val error = loadGap(continuation.key)
                    return if (error != null) MediatorResult.Error(error) else MediatorResult.Success(false)
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
        val wasInteracting = viewport.value.interacting
        if (value.interacting && !wasInteracting) state.update { it.copy(interactionGeneration = it.interactionGeneration + 1) }
        viewport.value = value
        requestBoundaryCheck()
        if (value.interacting && !wasInteracting) launchIo { repository.selectTab(record.tabId) }
        checkpoint?.cancel()
        if (!value.interacting && value.itemKey != null && state.value.position == null && value.offset.isFinite()) {
            checkpoint =
                launchIo {
                    delay(200)
                    repository.checkpoint(record, activeKey, value.itemKey, value.offset)
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
                database.connect {
                    val latest = repository.current(record) ?: return@connect
                    repository.saveSnapshot(record, latest.copy(pendingReady = false))
                    repository.deleteData(pendingKey)
                }
                val pending = mediator(pendingKey)
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
                    publishInitial()
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
                                    val known = existingIds(bridge.second.data)
                                    active.saveResponse(
                                        bridge.first,
                                        bridge.second.copy(data = bridge.second.data.filter { it.timeline.statusId !in known }),
                                    )
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
        if (!valid() || state.value.interactionGeneration != generation || !state.value.hasNewContent) return
        val first = visibleRow(activeKey, 0)?.timeline
        val latest = repository.current(record) ?: return
        val boundary = latest.newContentBoundaryId?.let { database.pagingTimelineDao().readingItem(activeKey, "${activeKey}_$it") }
        val hasNew = first != null && (boundary == null || first.sortId < boundary.sortId)
        if (state.value.interactionGeneration != generation || !valid()) return
        if (hasNew && state.value.interactionGeneration == generation) {
            requestPosition("${activeKey}_${first.statusId}", 0.0, latest = true, generation = generation)
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

    private suspend fun publishInitial() {
        val first = visibleRow(pendingKey, 0)?.timeline
        currentCoroutineContext().ensureActive()
        // Commit the first page and its in-memory namespace together, even if refresh is cancelled.
        withContext(NonCancellable) {
            val published =
                database.connect {
                    val latest = repository.current(record) ?: return@connect false
                    repository.saveSnapshot(
                        record,
                        latest.copy(
                            activeKey = pendingKey,
                            pendingKey = activeKey,
                            anchorId = first?.let { "${pendingKey}_${it.statusId}" },
                            anchorSortId = first?.sortId,
                            offset = 0.0,
                            hasNewContent = false,
                            pendingReady = false,
                            newContentBoundaryId = null,
                        ),
                    )
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
        database.timelineReadingSessionDao().deleteSegment(key)
        repository.deleteData(key)
        visitedGaps -= key
        gapLoads.update { it - key }
    }

    private suspend fun hasEquivalentCursor(key: String): Boolean {
        val dao = database.pagingTimelineDao()

        suspend fun cursors(prefix: String) =
            dao.keysWithPrefix(prefix).associate { source ->
                val cursor = dao.getPagingKey(source)
                source.removePrefix(prefix) to
                    Triple(
                        cursor?.nextKey,
                        cursor?.prevKey,
                        if (source == prefix) emptyList() else dao.getByPagingKey(source).map { it.statusId },
                    )
            }
        val pending = cursors(key)
        return (listOf(activeKey) + database.timelineReadingSessionDao().segments(record.key).map { it.key })
            .any { it != key && cursors(it) == pending }
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
        if (joined || response.nextKey == null || (!continuous && hasEquivalentCursor(pendingKey))) {
            discardSegment(pendingKey)
        } else {
            database.timelineReadingSessionDao().saveSegment(
                DbTimelineReadingSegment(
                    pendingKey,
                    record.key,
                    boundary.statusId,
                    dao.readingItem(activeKey, "${activeKey}_${boundary.statusId}")?.sortId ?: boundary.sortId,
                ),
            )
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
                    val rebased = dao.getByPagingKey(activeKey).mapIndexed { index, row -> row.copy(sortId = index * 1_000_000L) }
                    dao.insertAll(rebased)
                    val byId = rebased.associateBy { it.statusId }
                    database.timelineReadingSessionDao().segments(record.key).forEach { segment ->
                        byId[segment.boundaryId]?.let { boundary ->
                            database.timelineReadingSessionDao().saveSegment(segment.copy(boundarySortId = boundary.sortId))
                        }
                    }
                    repository.current(record)?.let { snapshot ->
                        val anchor = snapshot.anchorId?.let { dao.readingItem(activeKey, it) }
                        repository.saveSnapshot(record, snapshot.copy(anchorSortId = anchor?.sortId))
                    }
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

    internal suspend fun loadGap(key: String): Exception? =
        withContext(PlatformDispatchers.IO) {
            mutex.withLock {
                if (!valid()) return@withLock null
                var segment =
                    database.timelineReadingSessionDao().segments(record.key).firstOrNull { it.key == key && !it.exhausted }
                        ?: return@withLock null
                gapLoads.update { it + (key to true) }
                gapGeneration.update { it + 1 }
                try {
                    // A viewport visit loads at most two pages; long gaps never block reading either side.
                    repeat(2) {
                        val dao = database.pagingTimelineDao()
                        val cursor = dao.getPagingKey(key)?.nextKey ?: return@repeat
                        val request = PagingRequest.Append(cursor)
                        val source = mediator(key)
                        val response = source.load(40, request)
                        if (!valid()) return@withLock null
                        val oldIds = dao.getByPagingKey(key).map { it.statusId }.toSet()
                        val oldCursors = dao.keysWithPrefix(key).associateWith { dao.getPagingKey(it) }
                        currentCoroutineContext().ensureActive()
                        withContext(NonCancellable) {
                            database.connect {
                                if (!valid()) return@connect
                                source.saveResponse(request, response)
                                val nextCursors = dao.keysWithPrefix(key).associateWith { dao.getPagingKey(it) }
                                check(
                                    response.nextKey == null || oldCursors != nextCursors ||
                                        response.data.any { it.timeline.statusId !in oldIds },
                                ) {
                                    "Timeline pagination did not advance"
                                }
                                val boundary =
                                    dao.readingItem(activeKey, "${activeKey}_${segment.boundaryId}")
                                        ?: dao.nearestReadingItem(activeKey, segment.boundarySortId)
                                val boundaryId = boundary?.statusId ?: segment.boundaryId
                                val overlaps =
                                    continuous &&
                                        existingRows(response.data).values.any {
                                            it.sortId >= (boundary?.sortId ?: segment.boundarySortId)
                                        }
                                if (continuous) {
                                    insertContinuousRows(response.data, boundaryId)
                                } else {
                                    // Ranked cursors do not identify missing posts between two cached rows.
                                    insertSegmentRows(response.data, null)
                                }
                                if (response.nextKey == null || overlaps) {
                                    discardSegment(key)
                                } else {
                                    segment =
                                        segment.copy(
                                            boundaryId = boundaryId,
                                            boundarySortId =
                                                dao.readingItem(activeKey, "${activeKey}_$boundaryId")?.sortId ?: segment.boundarySortId,
                                        )
                                    database.timelineReadingSessionDao().saveSegment(segment)
                                    val keep = response.data.map { it.timeline.statusId }.toSet()
                                    val stale = dao.getByPagingKey(key).filter { it.statusId !in keep }
                                    dao.deletePresentationReferences(key, stale.map { it._id })
                                    dao.delete(stale)
                                }
                            }
                            boundaryVersion.update { it + 1 }
                            gapGeneration.update { it + 1 }
                        }
                    }
                    null
                } catch (error: kotlinx.coroutines.CancellationException) {
                    throw error
                } catch (error: Exception) {
                    gapLoads.update { it + (key to false) }
                    notifyError(error)
                    error
                } finally {
                    gapLoads.update { if (it[key] == true) it - key else it }
                    gapGeneration.update { it + 1 }
                }
            }
        }

    private suspend fun readingBoundaries(): List<ReadingBoundary> =
        boundaryMutex.withLock {
            val version = boundaryVersion.value
            boundaryCache?.takeIf { it.first == version }?.let { return@withLock it.second }
            val dao = database.pagingTimelineDao()
            val segments = database.timelineReadingSessionDao().segments(record.key).filterNot { it.exhausted }
            val first = if (continuous && segments.isNotEmpty()) visibleRow(activeKey, 0)?.timeline else null
            val boundaries =
                if (continuous) {
                    segments.mapNotNull { segment ->
                        val boundary =
                            dao.readingItem(activeKey, "${activeKey}_${segment.boundaryId}")
                                ?: dao.nearestReadingItem(activeKey, segment.boundarySortId) ?: return@mapNotNull null
                        if (first == null || first.sortId >= boundary.sortId) return@mapNotNull null
                        val visible =
                            visibleRow(activeKey, dao.readingItemOffset(activeKey, boundary.sortId))?.timeline
                                ?: return@mapNotNull null
                        ReadingBoundary(segment, visible, dao.readingItemOffset(activeKey, visible.sortId), after = false)
                    }
                } else {
                    // Finish one ranked batch at a time; overlaps never discard another batch's unseen results.
                    val segment = segments.lastOrNull()
                    val tail = dao.readingTail(activeKey)
                    if (segment != null && tail != null) {
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
            if (boundaryVersion.value == version) boundaryCache = version to boundaries
            boundaries
        }

    private suspend fun updateReadingBoundary(value: ReadingViewport) {
        val toLoad =
            mutex.withLock {
                if (!valid() || viewport.value.interacting || viewport.value.itemKey != value.itemKey) return@withLock null
                val dao = database.pagingTimelineDao()
                val row = value.itemKey?.let { dao.readingItem(activeKey, it) } ?: return@withLock null
                val offset = dao.readingItemOffset(activeKey, row.sortId)
                if (value.atTop && state.value.position == null && visibleRow(activeKey, 0)?.timeline?.statusId == row.statusId) {
                    dismissReadNotice(force = true)
                }
                val boundaries = readingBoundaries()
                boundaries.filter { kotlin.math.abs(it.offset - offset) > 8 }.forEach { visitedGaps -= it.segment.key }
                boundaries
                    .firstOrNull {
                        kotlin.math.abs(it.offset - offset) <= 5 && gapLoads.value[it.segment.key] != false &&
                            it.segment.key !in visitedGaps
                    }?.segment
                    ?.key
                    ?.also { visitedGaps += it }
            }
        if (toLoad != null) loadGap(toLoad)
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
        val boundaries = readingBoundaries().groupBy { it.row.statusId }
        return items.flatMap { item ->
            val matching = boundaries[item.statusId] ?: return@flatMap listOf(item)
            val markers =
                matching.map { boundary ->
                    val segment = boundary.segment
                    val loading = gapLoads.value[segment.key] == true
                    val failed = gapLoads.value[segment.key] == false
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
                if (it.interactionGeneration == generation) it.copy(position = position) else it
            }.position
            ?.takeIf { it == position }
    }
}
