package dev.dimension.flare.data.datasource.microblog.paging

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import dev.dimension.flare.common.BaseRemoteMediator
import dev.dimension.flare.common.PlatformDispatchers
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.connect
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.database.cache.model.DbTimelineReadingSession
import dev.dimension.flare.data.database.cache.model.TranslationDisplayOptions
import dev.dimension.flare.data.datasource.microblog.MixedRemoteMediator
import dev.dimension.flare.data.repository.TimelineReadingRepository
import dev.dimension.flare.data.translation.PreTranslationService
import dev.dimension.flare.ui.model.TimelineReadingPosition
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal data class ReadingSessionState(
    val position: TimelineReadingPosition? = null,
    val hasNewContent: Boolean = false,
    val refreshing: Boolean = false,
)

internal data class ReadingViewport(
    val itemKey: String?,
    val offset: Double,
    val atTop: Boolean,
    val interacting: Boolean,
)

@OptIn(ExperimentalPagingApi::class)
internal class TimelineReadingSession(
    val record: DbTimelineReadingSession,
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
    val activeKey = "${record.key}active/"
    private val pendingKey = "${record.key}pending/"
    val state = MutableStateFlow(ReadingSessionState(hasNewContent = record.hasNewContent))
    private val viewport = MutableStateFlow(ReadingViewport(null, 0.0, atTop = false, interacting = false))
    private val mutex = Mutex()
    private var checkpoint: Job? = null
    private var startupPending = false
    private var active = mediator(activeKey)
    private val pending = mediator(pendingKey)

    private suspend fun valid(): Boolean = database.timelineReadingSessionDao().get(record.tabId)?.key == record.key

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
        startupPending = dao.anyPaging(activeKey)
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
        if (database
                .pagingTimelineDao()
                .getTimelinePageIdentities(
                    activeKey,
                    0,
                    1,
                ).isNotEmpty()
        ) {
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
        val value = viewport.value
        if (state.value.position == null && value.itemKey != null && value.offset.isFinite()) {
            checkpoint?.cancel()
            repository.savePosition(record, activeKey, value.itemKey, value.offset)
        }
    }

    fun updateViewport(value: ReadingViewport) {
        val wasInteracting = viewport.value.interacting
        viewport.value = value
        if (value.interacting && !wasInteracting) scope.launch { repository.selectTab(record.tabId) }
        checkpoint?.cancel()
        if (!value.interacting && value.itemKey != null && state.value.position == null && value.offset.isFinite()) {
            checkpoint =
                scope.launch(PlatformDispatchers.IO) {
                    delay(200)
                    val row = database.pagingTimelineDao().readingItem(activeKey, value.itemKey) ?: return@launch
                    database.timelineReadingSessionDao().savePosition(record.tabId, record.key, value.itemKey, row.sortId, value.offset)
                }
        }
    }

    fun positionRestored(id: Long) {
        if (state.value.position?.requestId != id) return
        state.update { if (it.position?.requestId == id) it.copy(position = null) else it }
        finishStartup()
    }

    fun cancelRestoration() {
        state.update { it.copy(position = null) }
        finishStartup()
    }

    private fun finishStartup() {
        if (!startupPending) return
        startupPending = false
        scope.launch {
            if (refreshOnLaunch()) refresh(automatic = false, initial = true)
        }
    }

    suspend fun refresh(
        automatic: Boolean,
        initial: Boolean = false,
    ) = withContext(PlatformDispatchers.IO) {
        mutex.withLock {
            if (!valid()) return@withLock
            state.update { it.copy(refreshing = true) }
            val previousPosition = state.value.position
            try {
                database.connect {
                    val latest = database.timelineReadingSessionDao().get(record.tabId)?.takeIf { it.key == record.key } ?: return@connect
                    database.timelineReadingSessionDao().save(latest.copy(pendingReady = false))
                    repository.deleteData(pendingKey)
                }
                val response = pending.load(40, PagingRequest.Refresh)
                if (!valid()) return@withLock
                pending.saveResponse(PagingRequest.Refresh, response)
                val hasVisibleRows = response.data.any { isVisible(it.asUi(pendingKey)) }
                database.connect {
                    val latest = database.timelineReadingSessionDao().get(record.tabId)?.takeIf { it.key == record.key } ?: return@connect
                    database.timelineReadingSessionDao().save(latest.copy(pendingReady = hasVisibleRows))
                }
                val currentIds = database.pagingTimelineDao().getStatusIdsWithInlineParents(activeKey).toSet()
                val newVisible = response.data.any { it.timeline.statusId !in currentIds && isVisible(it.asUi(pendingKey)) }
                if (database.pagingTimelineDao().getTimelinePageIdentities(activeKey, 0, 1).isEmpty()) {
                    publishLatest(response, requestScroll = false)
                } else if (newVisible) {
                    val bridge = if (!initial) continuousNewPage(response) else null
                    if (bridge != null) {
                        val position = viewport.value
                        val head = database.pagingTimelineDao().getTimelinePageIdentities(activeKey, 0, 1).firstOrNull()
                        val followLatest =
                            automatic && position.atTop && !position.interacting && state.value.position == null &&
                                position.itemKey == head?.let { "${activeKey}_${it.statusId}" }
                        database.connect {
                            if (!valid()) return@connect
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
                            val latest = database.timelineReadingSessionDao().get(record.tabId) ?: return@connect
                            database.timelineReadingSessionDao().save(latest.copy(hasNewContent = true, pendingReady = true))
                        }
                        state.update { it.copy(hasNewContent = true) }
                        if (followLatest && !viewport.value.interacting && viewport.value.itemKey == position.itemKey &&
                            kotlin.math.abs(viewport.value.offset - position.offset) < 1.0
                        ) {
                            showLatestLocked()
                        }
                    } else {
                        database.connect {
                            val latest =
                                database.timelineReadingSessionDao().get(record.tabId)?.takeIf { it.key == record.key } ?: return@connect
                            database.timelineReadingSessionDao().save(latest.copy(hasNewContent = true, pendingReady = true))
                        }
                        state.update { it.copy(hasNewContent = true) }
                    }
                }
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                state.update { it.copy(position = previousPosition) }
                notifyError(error)
                if (initial && !database.pagingTimelineDao().anyPaging(activeKey)) throw error
            } finally {
                state.update { it.copy(refreshing = false) }
            }
        }
    }

    // Only a loader's forward cursor can prove continuity; overlapping ranked pages cannot.
    private suspend fun continuousNewPage(
        latest: PagingResult<DbPagingTimelineWithStatus>,
    ): Pair<PagingRequest.Prepend, PagingResult<DbPagingTimelineWithStatus>>? {
        if ((loader as? CacheableRemoteLoader<*>)?.supportPrepend != true) return null
        val cursor = database.pagingTimelineDao().getPagingKey(activeKey)?.prevKey ?: return null
        val request = PagingRequest.Prepend(cursor)
        val forward = active.load(40, request)
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
            if (state.value.hasNewContent && database.timelineReadingSessionDao().get(record.tabId)?.pendingReady != true) {
                refresh(automatic = false)
            }
            mutex.withLock {
                state.update { it.copy(refreshing = true) }
                val previousPosition = state.value.position
                try {
                    showLatestLocked()
                } catch (error: kotlinx.coroutines.CancellationException) {
                    throw error
                } catch (error: Exception) {
                    state.update { it.copy(position = previousPosition) }
                    notifyError(error)
                } finally {
                    state.update { it.copy(refreshing = false) }
                }
            }
        }

    private suspend fun showLatestLocked() {
        if (!valid()) return
        val ready = database.timelineReadingSessionDao().get(record.tabId)?.pendingReady == true
        if (ready) {
            val rows = database.pagingTimelineDao().getTimelinePage(pendingKey, 0, 40)
            publishLatest(PagingResult(rows), requestScroll = true)
        } else if (!state.value.hasNewContent) {
            database.pagingTimelineDao().getTimelinePageIdentities(activeKey, 0, 1).firstOrNull()?.let {
                requestPosition("${activeKey}_${it.statusId}", 0.0, latest = true)
            }
        }
    }

    private suspend fun publishLatest(
        response: PagingResult<DbPagingTimelineWithStatus>,
        requestScroll: Boolean,
    ) {
        val first = visibleRow(pendingKey, 0)?.timeline
        if (requestScroll && first == null) return
        val previousPosition = state.value.position
        if (requestScroll && first != null) requestPosition("${activeKey}_${first.statusId}", 0.0, latest = true)
        var published = false
        database.connect {
            val latest = database.timelineReadingSessionDao().get(record.tabId)?.takeIf { it.key == record.key } ?: return@connect
            repository.deleteData(activeKey)
            repository.copyData(pendingKey, activeKey)
            database.timelineReadingSessionDao().save(
                latest.copy(
                    anchorId = first?.let { "${activeKey}_${it.statusId}" },
                    anchorSortId = first?.sortId,
                    offset = 0.0,
                    hasNewContent = false,
                    pendingReady = false,
                ),
            )
            repository.deleteData(pendingKey)
            published = true
        }
        if (!published) {
            state.update { it.copy(position = previousPosition) }
            return
        }
        // Mixed loaders also retain which sources are exhausted in memory.
        active = mediator(activeKey)
        state.update { it.copy(hasNewContent = false) }
    }

    private suspend fun requestPosition(
        key: String,
        offset: Double,
        latest: Boolean = false,
    ) {
        val position =
            TimelineReadingPosition(
                key,
                offset,
                dev.dimension.flare.common.SnowflakeIdGenerator
                    .nextId(),
                latest,
            )
        state.update { it.copy(position = position) }
    }
}
