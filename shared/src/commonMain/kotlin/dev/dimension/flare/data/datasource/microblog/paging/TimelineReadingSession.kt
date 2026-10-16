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
    private var boundaryCheck: Job? = null
    private var startupPending = false
    private var active = mediator(activeKey)
    private val pagingGeneration = MutableStateFlow(0)
    val gapGeneration = MutableStateFlow(0)
    private val gapLoads = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    private val visitedGaps = mutableSetOf<String>()

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
                mutex.withLock { active.load(loadType, state) }
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
        boundaryCheck?.cancel()
        val value = viewport.value
        if (state.value.position == null && value.itemKey != null && value.offset.isFinite()) {
            repository.checkpoint(record, activeKey, value.itemKey, value.offset)
        }
        repository.release(record)
    }

    fun updateViewport(value: ReadingViewport) {
        if (closed.value) return
        if (value.itemKey?.startsWith("reading-gap:") == true) return
        val wasInteracting = viewport.value.interacting
        if (value.interacting && !wasInteracting) state.update { it.copy(interactionGeneration = it.interactionGeneration + 1) }
        viewport.value = value
        if (value.itemKey != null && state.value.position == null) {
            boundaryCheck?.cancel()
            boundaryCheck = scope.launch { updateReadingBoundary(value) }
        }
        if (value.interacting && !wasInteracting) scope.launch { repository.selectTab(record.tabId) }
        checkpoint?.cancel()
        if (!value.interacting && value.itemKey != null && state.value.position == null && value.offset.isFinite()) {
            checkpoint =
                scope.launch(PlatformDispatchers.IO) {
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
                val currentIds = database.pagingTimelineDao().getStatusIdsWithInlineParents(activeKey).toSet()
                val oldHead = visibleRow(activeKey, 0)?.timeline?.statusId
                val newVisible = response.data.any { it.timeline.statusId !in currentIds && isVisible(it.asUi(pendingKey)) }
                if (database.pagingTimelineDao().getTimelinePageIdentities(activeKey, 0, 1).isEmpty()) {
                    publishInitial()
                } else if (newVisible) {
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
                                    // An overlap must update content without moving rows already being read.
                                    active.saveResponse(
                                        bridge.first,
                                        bridge.second.copy(
                                            data =
                                                bridge.second.data.filter {
                                                    it.timeline.statusId !in
                                                        currentIds
                                                },
                                        ),
                                    )
                                    repository.deleteData(pendingKey)
                                } else {
                                    attachSegment(response)
                                }
                                val latest = repository.current(record) ?: return@connect false
                                repository.saveSnapshot(
                                    record,
                                    latest.copy(
                                        hasNewContent = true,
                                        pendingReady = false,
                                        pendingKey = nextPendingKey,
                                        newContentBoundaryId = latest.newContentBoundaryId ?: oldHead,
                                    ),
                                )
                                true
                            }
                        if (!attached) return@withContext
                        pendingKey = nextPendingKey
                        state.update { it.copy(hasNewContent = true) }
                        gapGeneration.update { it + 1 }
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
        if ((loader as? CacheableRemoteLoader<*>)?.supportPrepend != true) return null
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

    private suspend fun attachSegment(response: PagingResult<DbPagingTimelineWithStatus>) {
        val dao = database.pagingTimelineDao()
        val boundary = dao.getByPagingKey(activeKey).firstOrNull() ?: return
        insertSegmentRows(response.data, boundary.statusId)
        database.timelineReadingSessionDao().saveSegment(
            DbTimelineReadingSegment(
                pendingKey,
                record.key,
                boundary.statusId,
                dao.readingItem(activeKey, "${activeKey}_${boundary.statusId}")?.sortId ?: boundary.sortId,
                exhausted = response.nextKey == null,
            ),
        )
        if (response.nextKey == null) repository.deleteData(pendingKey)
    }

    private suspend fun insertSegmentRows(
        data: List<DbPagingTimelineWithStatus>,
        beforeId: String,
    ) {
        val dao = database.pagingTimelineDao()
        var existing = dao.getByPagingKey(activeKey)
        val seen = dao.getStatusIdsWithInlineParents(activeKey).toSet()
        val additions = data.distinctBy { it.timeline.statusId }.filter { it.timeline.statusId !in seen }
        val orderedByTime = (loader as? MixedRemoteMediator)?.ordersByTime == true
        val sortIds =
            if (orderedByTime) {
                additions.map { requireNotNull((loader as SortIdProvider).sortId(it.asUi(activeKey))) }
            } else {
                var index = existing.indexOfFirst { it.statusId == beforeId }.takeIf { it >= 0 } ?: existing.size

                fun room(): Long {
                    val right = existing.getOrNull(index)?.sortId ?: Long.MAX_VALUE / 2
                    val left = existing.getOrNull(index - 1)?.sortId ?: (right - (additions.size + 1L) * 1_000_000L)
                    return (right - left) / (additions.size + 1L)
                }
                if (room() <= 0 || existing.firstOrNull()?.sortId?.let { it < Long.MIN_VALUE / 2 } == true) {
                    existing = existing.mapIndexed { i, row -> row.copy(sortId = i * 1_000_000L) }
                    dao.insertAll(existing)
                    val rebased = existing.associateBy { it.statusId }
                    database.timelineReadingSessionDao().segments(record.key).forEach { segment ->
                        rebased[segment.boundaryId]?.let { boundary ->
                            database.timelineReadingSessionDao().saveSegment(segment.copy(boundarySortId = boundary.sortId))
                        }
                    }
                    index = existing.indexOfFirst { it.statusId == beforeId }.takeIf { it >= 0 } ?: existing.size
                    repository.current(record)?.let { snapshot ->
                        val anchor = snapshot.anchorId?.let { dao.readingItem(activeKey, it) }
                        repository.saveSnapshot(record, snapshot.copy(anchorSortId = anchor?.sortId))
                    }
                }
                val step = room()
                val right = existing.getOrNull(index)?.sortId ?: Long.MAX_VALUE / 2
                additions.indices.map { right - step * (additions.size - it) }
            }
        val existingById = existing.associateBy { it.statusId }
        val newSortIds = additions.mapIndexed { index, row -> row.timeline.statusId to sortIds[index] }.toMap()
        val updated =
            data.distinctBy { it.timeline.statusId }.mapNotNull { item ->
                val sortId = existingById[item.timeline.statusId]?.sortId ?: newSortIds[item.timeline.statusId] ?: return@mapNotNull null
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
    }

    internal suspend fun loadGap(key: String) =
        withContext(PlatformDispatchers.IO) {
            mutex.withLock {
                if (!valid()) return@withLock
                var segment =
                    database.timelineReadingSessionDao().segments(record.key).firstOrNull { it.key == key && !it.exhausted }
                        ?: return@withLock
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
                        if (!valid()) return@withLock
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
                                val overlaps =
                                    response.data.any {
                                        dao.readingItem(activeKey, "${activeKey}_${it.timeline.statusId}") != null &&
                                            it.timeline.statusId !in oldIds
                                    }
                                val boundary =
                                    dao.readingItem(activeKey, "${activeKey}_${segment.boundaryId}")
                                        ?: dao.nearestReadingItem(activeKey, segment.boundarySortId)
                                val boundaryId = boundary?.statusId ?: segment.boundaryId
                                insertSegmentRows(response.data, boundaryId)
                                segment =
                                    segment.copy(
                                        boundaryId = boundaryId,
                                        boundarySortId =
                                            dao.readingItem(activeKey, "${activeKey}_$boundaryId")?.sortId ?: segment.boundarySortId,
                                        exhausted =
                                            response.nextKey == null ||
                                                (overlaps && (loader as? CacheableRemoteLoader<*>)?.supportPrepend == true),
                                    )
                                if (overlaps && (loader as? CacheableRemoteLoader<*>)?.supportPrepend == true) {
                                    database.timelineReadingSessionDao().deleteSegment(key)
                                } else {
                                    database.timelineReadingSessionDao().saveSegment(segment)
                                }
                                if (segment.exhausted) repository.deleteData(key)
                            }
                            gapGeneration.update { it + 1 }
                        }
                    }
                } catch (error: kotlinx.coroutines.CancellationException) {
                    throw error
                } catch (error: Exception) {
                    gapLoads.update { it + (key to false) }
                    notifyError(error)
                } finally {
                    gapLoads.update { if (it[key] == true) it - key else it }
                    gapGeneration.update { it + 1 }
                }
            }
        }

    private suspend fun updateReadingBoundary(value: ReadingViewport) {
        val toLoad =
            mutex.withLock {
                if (!valid() || viewport.value.itemKey != value.itemKey) return@withLock emptyList<String>()
                val dao = database.pagingTimelineDao()
                val row = value.itemKey?.let { dao.readingItem(activeKey, it) } ?: return@withLock emptyList<String>()
                val offset = dao.readingItemOffset(activeKey, row.sortId)
                if (value.atTop && !value.interacting && state.value.position == null &&
                    visibleRow(activeKey, 0)?.timeline?.statusId == row.statusId
                ) {
                    dismissReadNotice(force = true)
                }
                val firstVisible = visibleRow(activeKey, 0)?.timeline ?: return@withLock emptyList<String>()
                database.timelineReadingSessionDao().segments(record.key).mapNotNull { segment ->
                    val boundary =
                        dao.readingItem(activeKey, "${activeKey}_${segment.boundaryId}")
                            ?: dao.nearestReadingItem(activeKey, segment.boundarySortId)
                    if (boundary == null || firstVisible.sortId >= boundary.sortId) return@mapNotNull null
                    val visibleBoundary = visibleRow(activeKey, dao.readingItemOffset(activeKey, boundary.sortId))
                    val distance =
                        visibleBoundary?.let { kotlin.math.abs(dao.readingItemOffset(activeKey, it.timeline.sortId) - offset) }
                            ?: Int.MAX_VALUE
                    if (distance > 8) visitedGaps -= segment.key
                    if (distance <= 5 && !segment.exhausted && gapLoads.value[segment.key] != false &&
                        visitedGaps.add(segment.key)
                    ) {
                        segment.key
                    } else {
                        null
                    }
                }
            }
        toLoad.forEach { key -> scope.launch { loadGap(key) } }
    }

    suspend fun filtersChanged() =
        mutex.withLock {
            if (!valid()) return@withLock
            dismissReadNotice()
            gapGeneration.update { it + 1 }
        }

    internal suspend fun withGapMarkers(items: List<TimelinePageItem>): List<TimelinePageItem> {
        if (items.isEmpty()) return items
        val dao = database.pagingTimelineDao()
        val markers =
            database
                .timelineReadingSessionDao()
                .segments(record.key)
                .mapNotNull { segment ->
                    val boundary =
                        dao.readingItem(activeKey, "${activeKey}_${segment.boundaryId}")
                            ?: dao.nearestReadingItem(activeKey, segment.boundarySortId) ?: return@mapNotNull null
                    val visible = visibleRow(activeKey, dao.readingItemOffset(activeKey, boundary.sortId)) ?: return@mapNotNull null
                    if (items.none { it.statusId == visible.timeline.statusId }) return@mapNotNull null
                    // Filtering away a whole new batch must not leave an orphan divider.
                    if (visibleRow(activeKey, 0)?.timeline?.sortId?.let { it < boundary.sortId } != true) return@mapNotNull null
                    val loading = gapLoads.value[segment.key] == true
                    val failed = gapLoads.value[segment.key] == false
                    val kind =
                        when {
                            loading -> UiTimelineV2.Message.Type.Localized.MessageId.ReadingGapLoading
                            failed -> UiTimelineV2.Message.Type.Localized.MessageId.ReadingGapFailed
                            segment.exhausted -> UiTimelineV2.Message.Type.Localized.MessageId.ReadingBatchBoundary
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
                            readingGap = TimelineReadingGap(loading, failed, !segment.exhausted) { scope.launch { loadGap(segment.key) } },
                        )
                    visible.timeline.statusId to
                        TimelinePageItem(
                            DbTimelinePageIdentity(segment.key, boundary.sortId, marker.renderHash, 0),
                            marker,
                            emptyMap(),
                        )
                }.groupBy({ it.first }, { it.second })
        return items.flatMap { markers[it.statusId].orEmpty() + it }
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
