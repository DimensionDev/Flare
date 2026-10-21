package dev.dimension.flare.data.datasource.microblog.paging

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator.MediatorResult
import dev.dimension.flare.common.SnowflakeIdGenerator
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.connect
import dev.dimension.flare.data.database.cache.mapper.saveToDatabase
import dev.dimension.flare.data.database.cache.model.DbPagingKey
import dev.dimension.flare.data.database.cache.model.DbPagingTimeline
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.database.cache.model.DbStatus
import dev.dimension.flare.data.datasource.microblog.MixedRemoteMediator
import dev.dimension.flare.data.translation.NoopPreTranslationService
import dev.dimension.flare.data.translation.PreTranslationService
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.DbAccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.ReferenceType
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.asTimelinePostItem
import dev.dimension.flare.ui.render.toUi
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.uuid.Uuid

internal data class GapLoadResult(
    val insertedKeys: List<String> = emptyList(),
    val failure: Throwable? = null,
)

@OptIn(ExperimentalPagingApi::class)
internal open class TimelineRemoteMediator(
    private val loader: CacheableRemoteLoader<UiTimelineV2>,
    private val database: CacheDatabase,
    private val allowLongText: Boolean,
    private val notifyError: (Throwable) -> Unit = {},
    private val preTranslationService: PreTranslationService = NoopPreTranslationService,
    private val refreshOnInitialize: suspend () -> Boolean = { true },
    private val retainHistory: Boolean? = null,
    private val accountKeys: Set<MicroBlogKey> = emptySet(),
    private val refreshSession: HomeTimelineRefreshSessions.Session? = null,
    private val retainedHome: suspend () -> TimelineRemoteMediator? = { null },
) : BasePagingRemoteMediator<
        OffsetFromStartPagingKey,
        TimelinePageItem,
        DbPagingTimelineWithStatus,
    >(
        database = database,
    ),
    RemoteLoader<DbPagingTimelineWithStatus> {
    private var suppressInitialPrepend = false
    private var refreshSharedHomeOnPrepend = false
    private val ordersByTime = (loader as? MixedRemoteMediator)?.pageLoader()?.ordersByTime == true

    override val pagingKey: String
        get() = loader.pagingKey

    init {
        if (loader is ReportableRemoteLoader) {
            loader.reportError = notifyError
        }
    }

    override fun onError(e: Throwable) {
        notifyError(e)
    }

    override suspend fun initialize(): InitializeAction {
        val hasCache = database.pagingTimelineDao().anyPaging(pagingKey)
        if (!hasCache) {
            return InitializeAction.LAUNCH_INITIAL_REFRESH
        }

        val shouldRefresh = refreshOnInitialize()
        suppressInitialPrepend = loader.supportPrepend && !shouldRefresh
        refreshSharedHomeOnPrepend = retainHistory == null && loader.supportPrepend && shouldRefresh
        return if (!shouldRefresh || (loader.supportPrepend && retainHistory != true)) {
            InitializeAction.SKIP_INITIAL_REFRESH
        } else {
            InitializeAction.LAUNCH_INITIAL_REFRESH
        }
    }

    override suspend fun doLoad(
        loadType: LoadType,
        state: PagingState<OffsetFromStartPagingKey, TimelinePageItem>,
    ): MediatorResult {
        if (loadType == LoadType.PREPEND && suppressInitialPrepend) {
            suppressInitialPrepend = false
            return MediatorResult.Success(endOfPaginationReached = true)
        }
        if (loadType == LoadType.REFRESH) {
            suppressInitialPrepend = false
            refreshSharedHomeOnPrepend = false
        }
        return when {
            retainHistory == null -> {
                val home = retainedHome()
                if (home != null && loadType == LoadType.PREPEND && refreshSharedHomeOnPrepend) {
                    // Ordinary prepend sources use this boundary for their initial head refresh.
                    when (val result = home.load(LoadType.REFRESH, state)) {
                        is MediatorResult.Error -> {
                            result
                        }

                        is MediatorResult.Success -> {
                            refreshSharedHomeOnPrepend = false
                            MediatorResult.Success(true)
                        }
                    }
                } else {
                    (home?.load(loadType, state) ?: super.doLoad(loadType, state)).also {
                        if (loadType == LoadType.PREPEND && it is MediatorResult.Success) refreshSharedHomeOnPrepend = false
                    }
                }
            }

            loadType == LoadType.REFRESH && refreshSession != null -> {
                refreshSession.mutex.withLock { loadHomePage(loadType, state.config.pageSize) }
            }

            else -> {
                loadHomePage(loadType, state.config.pageSize)
            }
        }
    }

    private suspend fun loadHomePage(
        loadType: LoadType,
        pageSize: Int,
    ): MediatorResult {
        val dao = database.pagingTimelineDao()
        val before =
            database.connect {
                currentCoroutineContext().ensureActive()
                dao.getPagingKeyMetadata(pagingKey) ?: DbPagingKey(pagingKey, accountKeys = Json.encodeToString(accountKeys))
                    .also { dao.insertPagingKey(it) }
            }
        if (loadType == LoadType.PREPEND && retainHistory == true) return MediatorResult.Success(true)
        val request =
            when (loadType) {
                LoadType.REFRESH -> PagingRequest.Refresh
                LoadType.APPEND -> PagingRequest.Append(dao.getNextKey(pagingKey) ?: return MediatorResult.Success(true))
                LoadType.PREPEND -> PagingRequest.Prepend(before.prevKey ?: return MediatorResult.Success(true))
            }
        val sourceHeads = Json.decodeFromString<Map<String, List<String>>>(before.sourceHeads)
        val boundary =
            if (loadType == LoadType.REFRESH && retainHistory == true) {
                sourceHeads
            } else {
                emptyMap()
            }
        val refreshWithoutGap =
            refreshSession
                ?.refreshedSources
                .orEmpty()
                .takeIf {
                    retainHistory == true && loadType == LoadType.REFRESH && it.isNotEmpty() && dao.getFirstRow(pagingKey) != null
                }.orEmpty()
        val result =
            fetchPage(
                pageSize,
                request,
                boundary,
                checkCachedItems = retainHistory == true && loadType == LoadType.REFRESH,
                refreshWithoutGap = refreshWithoutGap,
            )
        val mapped =
            TimelinePagingMapper.toDb(
                result.data,
                pagingKey,
                sortIds = result.data.map { item -> homeSortId(item) },
            )
        val requestContext = currentCoroutineContext()
        // Finish the commit and session update together if cancellation arrives during commit.
        withContext(NonCancellable) {
            val committed =
                database.connect {
                    requestContext.ensureActive()
                    if (dao.getPagingKeyMetadata(pagingKey) != before) return@connect false
                    val first = dao.getFirstRow(pagingKey)
                    val (gapNext, tailUpdates) =
                        if (retainHistory == true && loadType == LoadType.REFRESH && first != null && loader is MixedRemoteMediator) {
                            loader.pageLoader().splitContinuation(result.nextKey, boundary.keys)
                        } else {
                            result.nextKey to null
                        }
                    val updatedTail = mergeTailContinuation(tailUpdates)
                    if (retainHistory == true && loadType == LoadType.APPEND && result.nextKey != null) {
                        val existing = existingRows(mapped)
                        check(
                            continuationIdentity(result.nextKey) != continuationIdentity((request as PagingRequest.Append).nextKey) ||
                                mapped.any { it.timeline._id !in existing },
                        ) {
                            "Timeline continuation did not advance"
                        }
                    }
                    if (retainHistory == true) {
                        val existing = existingRows(mapped)
                        val hasNewHead =
                            mapped.any {
                                it.timeline._id !in existing && (!ordersByTime || first == null || it.timeline.sortId < first.sortId)
                            }
                        val gap =
                            if (loadType == LoadType.REFRESH && first != null && hasNewHead && gapNext != null) {
                                UiTimelineV2.Gap(
                                    statusKey = MicroBlogKey(Uuid.random().toString(), "timeline-gap"),
                                    createdAt = Clock.System.now().toUi(),
                                    nextKey = gapNext,
                                    lowerBound = boundary,
                                )
                            } else {
                                null
                            }
                        val gapRows = if (gap != null) TimelinePagingMapper.toDb(listOf(gap), pagingKey) else emptyList()
                        if (ordersByTime) {
                            // Post ranks are even; a Gap occupies the slot just before its old lower neighbour.
                            saveToDatabase(
                                database,
                                mapped +
                                    gapRows.map {
                                        it.copy(timeline = it.timeline.copy(sortId = checkNotNull(first).sortId - 1))
                                    },
                            )
                        } else {
                            val overlap = if (gapRows.isNotEmpty()) existing.values.maxByOrNull { it.sortId } else null
                            savePreservingOrder(
                                mapped + if (overlap == null) gapRows else emptyList(),
                                beforeId = first?.takeIf { loadType == LoadType.REFRESH }?._id,
                            )
                            if (overlap != null) {
                                // The continuation follows every cached item in this page, even on ranked overlap.
                                savePreservingOrder(gapRows, dao.getRowAfter(pagingKey, overlap._id)?._id)
                            }
                        }
                    } else {
                        onSaveCache(request, mapped)
                    }
                    dao.updatePage(
                        pagingKey = pagingKey,
                        nextKey = updatedTail ?: result.nextKey,
                        keepNextKey =
                            loadType == LoadType.PREPEND ||
                                (loadType == LoadType.REFRESH && retainHistory == true && first != null && updatedTail == null),
                        prevKey = if (loadType == LoadType.APPEND) before.prevKey else result.previousKey,
                        sourceHeads =
                            Json.encodeToString(
                                if (loadType == LoadType.REFRESH) boundary + result.sourceHeads else result.sourceHeads + sourceHeads,
                            ),
                        accountKeys = Json.encodeToString(accountKeys),
                    )
                    enqueuePreTranslation(mapped)
                    requestContext.ensureActive()
                    true
                }
            if (committed && loadType == LoadType.REFRESH) refreshSession?.refreshedSources?.addAll(result.refreshedSources)
            check(committed) { "Timeline changed while loading; retry" }
        }
        result.sourceFailures.forEach(notifyError)
        return MediatorResult.Success(if (loadType == LoadType.REFRESH) false else result.nextKey == null)
    }

    /** Loads exactly one logical page; the gap itself is the compare-and-set token. */
    internal suspend fun loadGap(
        gap: UiTimelineV2.Gap,
        pageSize: Int = 20,
    ): GapLoadResult {
        check(retainHistory == true)
        val dao = database.pagingTimelineDao()
        val key = dao.getPagingKeyMetadata(pagingKey) ?: return GapLoadResult()
        val gapRow = TimelinePagingMapper.toDb(listOf(gap), pagingKey).single().timeline
        if (dao.getRow(pagingKey, gapRow._id) == null) return GapLoadResult()
        val result =
            try {
                if (hasJoinedSingleSourceGap(gap, gapRow._id)) {
                    PagingResult()
                } else {
                    fetchPage(pageSize, PagingRequest.Append(gap.nextKey), gap.lowerBound, checkCachedItems = true)
                }
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                notifyError(error)
                throw error
            }
        val mapped = TimelinePagingMapper.toDb(result.data, pagingKey, sortIds = result.data.map { homeSortId(it) })
        var insertedKeys = emptyList<String>()
        database.connect {
            currentCoroutineContext().ensureActive()
            val currentKey = dao.getPagingKeyMetadata(pagingKey) ?: return@connect
            if (currentKey.generation != key.generation) return@connect
            val row = dao.getRow(pagingKey, gapRow._id) ?: return@connect
            val current =
                database
                    .statusDao()
                    .getWithReferencesSync(gap.statusKey, AccountType.Guest)
                    ?.status
                    ?.data
                    ?.content as? UiTimelineV2.Gap ?: return@connect
            if (current.revision != gap.revision || current.nextKey != gap.nextKey) return@connect
            val (gapNext, tailUpdates) =
                if (loader is MixedRemoteMediator) {
                    loader.pageLoader().splitContinuation(result.nextKey, gap.lowerBound.keys)
                } else {
                    result.nextKey to null
                }
            val updatedTail = mergeTailContinuation(tailUpdates)
            val existing = existingRows(mapped)
            val overlap = existing.values.maxByOrNull { it.sortId }?.takeIf { it.sortId > row.sortId }
            check(
                continuationIdentity(gapNext) != continuationIdentity(gap.nextKey) || mapped.any { it.timeline._id !in existing },
            ) {
                "Timeline continuation did not advance"
            }
            if (ordersByTime) saveToDatabase(database, mapped) else savePreservingOrder(mapped, row._id)
            insertedKeys = mapped.filter { it.timeline._id !in existing }.map { "${pagingKey}_${it.timeline.statusId}" }.distinct()
            if (gapNext == null) {
                dao.delete(listOf(row))
                database.statusDao().delete(gap.statusKey, AccountType.Guest)
            } else {
                val next = gap.copy(nextKey = gapNext, revision = gap.revision + 1)
                val nextRow = TimelinePagingMapper.toDb(listOf(next), pagingKey).single()
                if (!ordersByTime && overlap != null) {
                    dao.delete(listOf(row))
                    savePreservingOrder(listOf(nextRow), dao.getRowAfter(pagingKey, overlap._id)?._id)
                } else {
                    saveToDatabase(
                        database,
                        listOf(
                            nextRow.copy(timeline = nextRow.timeline.copy(sortId = checkNotNull(dao.getRow(pagingKey, row._id)).sortId)),
                        ),
                    )
                }
            }
            val sourceHeads = Json.decodeFromString<Map<String, List<String>>>(currentKey.sourceHeads)
            val updatedHeads = result.sourceHeads + sourceHeads
            if (updatedHeads != sourceHeads || updatedTail != null) {
                dao.updatePage(
                    pagingKey = pagingKey,
                    nextKey = updatedTail,
                    keepNextKey = updatedTail == null,
                    prevKey = currentKey.prevKey,
                    sourceHeads = Json.encodeToString(updatedHeads),
                    accountKeys = currentKey.accountKeys,
                )
            }
            enqueuePreTranslation(mapped)
        }
        result.sourceFailures.forEach(notifyError)
        return GapLoadResult(insertedKeys, result.continuationFailure)
    }

    private suspend fun hasJoinedSingleSourceGap(
        gap: UiTimelineV2.Gap,
        gapId: String,
    ): Boolean {
        if (loader is MixedRemoteMediator || !loader.canJoinTimeline) return false
        val statusIds = gap.lowerBound[loader.pagingKey].orEmpty().mapNotNull(::boundaryStatusId)
        if (statusIds.isEmpty()) return false
        return database.connect {
            val dao = database.pagingTimelineDao()
            val row = dao.getRow(pagingKey, gapId) ?: return@connect false
            // Legacy ordered sources could advance their Gap beyond an already cached lower boundary.
            statusIds.chunked(500).any { ids ->
                dao.getByPagingKeyAndStatusIds(pagingKey, ids).any {
                    !it.isGap && it.notificationKey == null &&
                        it.sortId < row.sortId
                }
            }
        }
    }

    private fun boundaryStatusId(key: String): String? {
        // Post and Feed boundaries use length-prefixed stable identities; unknown formats keep loading.
        val parts = mutableListOf<String>()
        var offset = 0
        while (offset < key.length && parts.size < 6) {
            val separator = key.indexOf(':', offset)
            if (separator < 0) return null
            val length = key.substring(offset, separator).toIntOrNull() ?: return null
            offset = separator + 1
            if (length < 0 || length > key.length - offset) return null
            parts += key.substring(offset, offset + length)
            offset += length
        }
        if (offset != key.length || parts.size < 4) return null
        val kind = parts[parts.size - 3]
        if (kind != "post" && kind != "feed") return null
        val account: DbAccountType =
            when {
                parts.size == 4 && parts[0] == "guest" -> AccountType.Guest
                parts.size == 5 && parts[0] == "guest_host" -> AccountType.GuestHost(parts[1])
                parts.size == 6 && parts[0] == "specific" -> AccountType.Specific(MicroBlogKey(parts[1], parts[2]))
                else -> return null
            }
        return DbStatus.createId(account, MicroBlogKey(parts[parts.size - 2], parts.last()))
    }

    private suspend fun fetchPage(
        pageSize: Int,
        request: PagingRequest,
        boundary: Map<String, List<String>>,
        checkCachedItems: Boolean,
        refreshWithoutGap: Set<String> = emptySet(),
    ): PagingResult<UiTimelineV2> {
        val result =
            if (loader is MixedRemoteMediator) {
                loader.pageLoader().loadPage(
                    pageSize,
                    request,
                    cachedItemKeys = { if (checkCachedItems) cachedItemKeys(it) else emptySet() },
                    refreshWithoutGap = refreshWithoutGap,
                    publishAll =
                        checkCachedItems &&
                            (request is PagingRequest.Append || boundary.isNotEmpty() || database.pagingTimelineDao().anyPaging(pagingKey)),
                    boundary = boundary,
                )
            } else {
                loader.load(pageSize, request).let { response ->
                    val join =
                        if (loader.canJoinTimeline) {
                            response.data.indexOfFirst { it.stableItemKey in boundary[loader.pagingKey].orEmpty() }
                        } else {
                            -1
                        }
                    val pageIsCached =
                        checkCachedItems && request is PagingRequest.Append && join < 0 && loader.pagingKey !in refreshWithoutGap &&
                            response.nextKey != null && response.data.isNotEmpty() &&
                            response.continuationFailure == null && response.sourceFailures.isEmpty() &&
                            cachedItemKeys(response.data).containsAll(response.data.map { it.stableItemKey })
                    val data = if (join >= 0 && request is PagingRequest.Append) response.data.take(join + 1) else response.data
                    response.copy(
                        data = data,
                        nextKey = if (join >= 0 || pageIsCached || loader.pagingKey in refreshWithoutGap) null else response.nextKey,
                        sourceHeads = mapOf(loader.pagingKey to data.map { it.stableItemKey }),
                        refreshedSources =
                            if (request is PagingRequest.Refresh && loader.refreshGapOncePerSession &&
                                response.continuationFailure == null && response.sourceFailures.isEmpty()
                            ) {
                                setOf(loader.pagingKey)
                            } else {
                                emptySet()
                            },
                    )
                }
            }
        return if (loader.collapseReplyChains) result.copy(data = result.data.collapseReplyChains()) else result
    }

    private fun continuationIdentity(cursor: String?): String? =
        cursor?.let { if (loader is MixedRemoteMediator) loader.pageLoader().continuationIdentity(it) else it }

    private suspend fun mergeTailContinuation(updates: String?): String? {
        if (updates == null || loader !is MixedRemoteMediator) return null
        val previous = database.pagingTimelineDao().getNextKey(pagingKey)
        return loader.pageLoader().mergeTailContinuation(previous, updates)?.takeIf { it != previous }
    }

    private suspend fun homeSortId(item: UiTimelineV2): Long? =
        if (retainHistory == true && !ordersByTime) {
            null
        } else {
            (loader as? SortIdProvider)?.sortId(item)?.let { if (ordersByTime) it * 2 else it }
        }

    private suspend fun existingRows(data: List<DbPagingTimelineWithStatus>): Map<String, DbPagingTimeline> =
        data
            .map { it.timeline.statusId }
            .distinct()
            .chunked(500)
            .flatMap {
                database.pagingTimelineDao().getByPagingKeyAndStatusIds(pagingKey, it)
            }.associateBy { it._id }

    private suspend fun cachedItemKeys(items: List<UiTimelineV2>): Set<String> {
        if (items.isEmpty()) return emptySet()
        val mapped = TimelinePagingMapper.toDb(items, pagingKey)
        val existing = existingRows(mapped)
        return items.filterIndexed { index, _ -> mapped[index].timeline._id in existing }.mapTo(hashSetOf()) { it.stableItemKey }
    }

    private suspend fun savePreservingOrder(
        data: List<DbPagingTimelineWithStatus>,
        beforeId: String?,
    ) {
        val dao = database.pagingTimelineDao()
        var existing = existingRows(data)
        val count = data.distinctBy { it.timeline._id }.count { it.timeline._id !in existing }
        var upper = beforeId?.let { dao.getRow(pagingKey, it)?.sortId }
        var lower = dao.getPreviousSortId(pagingKey, upper)
        val step = 1_000_000L
        if ((upper != null && lower != null && upper - lower <= count) ||
            (upper != null && upper < Long.MIN_VALUE + (count + 1L) * step) ||
            (lower != null && lower > Long.MAX_VALUE - (count + 1L) * step)
        ) {
            // Only rebalance ranks when an insertion interval is exhausted; row IDs never change.
            dao.insertAll(dao.getByPagingKey(pagingKey).mapIndexed { index, row -> row.copy(sortId = index.toLong() * step) })
            existing = existingRows(data)
            upper = beforeId?.let { dao.getRow(pagingKey, it)?.sortId }
            lower = dao.getPreviousSortId(pagingKey, upper)
        }
        val stride = if (upper != null && lower != null) (upper - lower) / (count + 1L) else step
        var rank = lower ?: ((upper ?: 0L) - (count + 1L) * stride)
        saveToDatabase(
            database,
            data.distinctBy { it.timeline._id }.map { item ->
                val sortId = existing[item.timeline._id]?.sortId ?: (rank + stride).also { rank = it }
                item.copy(timeline = item.timeline.copy(sortId = sortId))
            },
        )
    }

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<DbPagingTimelineWithStatus> {
        val result =
            timeline(
                pageSize = pageSize,
                request = request,
            )
        val data =
            TimelinePagingMapper.toDb(
                data = result.data,
                pagingKey = pagingKey,
                sortIds = result.data.map { homeSortId(it) },
            )
        return PagingResult(
            data = data,
            nextKey = result.nextKey,
            previousKey = result.previousKey,
        )
    }

    suspend fun timeline(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> =
        loader
            .load(
                pageSize = pageSize,
                request = request,
            ).let { result ->
                result.sourceFailures.forEach(notifyError)
                result.copy(
                    data =
                        if (loader.collapseReplyChains) {
                            result.data.collapseReplyChains()
                        } else {
                            result.data
                        },
                )
            }

    override suspend fun onSaveCache(
        request: PagingRequest,
        data: List<DbPagingTimelineWithStatus>,
    ) {
        val dataToSave =
            if (request is PagingRequest.Prepend && loader.supportPrepend && data.isNotEmpty()) {
                val minimumSortId = database.pagingTimelineDao().getMinSortId(pagingKey)
                if (minimumSortId != null && minimumSortId >= Long.MIN_VALUE + data.size) {
                    val firstSortId = minimumSortId - data.size
                    data
                        .sortedBy { it.timeline.sortId }
                        .mapIndexed { index, item ->
                            item.copy(timeline = item.timeline.copy(sortId = firstSortId + index))
                        }
                } else {
                    if (minimumSortId != null) {
                        val rebasedRows =
                            database.pagingTimelineDao().getByPagingKey(pagingKey).map { row ->
                                row.copy(sortId = SnowflakeIdGenerator.nextId())
                            }
                        database.pagingTimelineDao().insertAll(rebasedRows)
                    }
                    data
                }
            } else if (request is PagingRequest.Append && data.isNotEmpty()) {
                // Repeated posts may have fresh content, but moving them to the end
                // shifts the items being read while the next page is loading.
                val existing =
                    data
                        .map { it.timeline.statusId }
                        .distinct()
                        .chunked(500)
                        .flatMap { statusIds ->
                            database.pagingTimelineDao().getByPagingKeyAndStatusIds(pagingKey, statusIds)
                        }.associateBy { it._id }
                data.map { item ->
                    existing[item.timeline._id]?.let { previous ->
                        item.copy(timeline = item.timeline.copy(sortId = previous.sortId))
                    } ?: item
                }
            } else {
                data
            }
        val staleTimeline =
            if (request is PagingRequest.Refresh) {
                val retainedTimelineIds =
                    dataToSave
                        .groupBy { it.timeline.pagingKey }
                        .mapValues { (_, rows) -> rows.mapTo(mutableSetOf()) { it.timeline._id } }
                (retainedTimelineIds.keys + pagingKey).flatMap { key ->
                    database
                        .pagingTimelineDao()
                        .getByPagingKey(key)
                        .filter { it._id !in retainedTimelineIds[key].orEmpty() }
                }
            } else {
                emptyList()
            }
        saveToDatabase(database, dataToSave)
        staleTimeline.groupBy { it.pagingKey }.forEach { (pagingKey, rows) ->
            database
                .pagingTimelineDao()
                .deletePresentationReferences(
                    pagingKey = pagingKey,
                    timelineIds = rows.map { it._id },
                )
        }
        if (staleTimeline.isNotEmpty()) {
            database.pagingTimelineDao().delete(staleTimeline)
        }
        enqueuePreTranslation(dataToSave)
    }

    protected fun enqueuePreTranslation(dataToSave: List<DbPagingTimelineWithStatus>) {
        preTranslationService.enqueueStatuses(
            dataToSave
                .flatMap { item ->
                    listOfNotNull(item.status.status.data) +
                        item.status.references.mapNotNull { it.status?.data } +
                        item.presentationReferences.flatMap { reference ->
                            listOfNotNull(reference.status?.status?.data) +
                                reference.status
                                    ?.references
                                    .orEmpty()
                                    .mapNotNull { it.status?.data }
                        }
                }.distinctBy { it.id },
            allowLongText = allowLongText,
        )
    }
}

internal fun List<UiTimelineV2>.collapseReplyChains(): List<UiTimelineV2> {
    fun UiTimelineV2.TimelinePostItem.key(): Pair<AccountType, MicroBlogKey> = accountType to statusKey

    val rootPosts =
        asSequence()
            .mapNotNull { it.asTimelinePostItem() }
            .associateBy { it.key() }
    if (rootPosts.isEmpty()) {
        return this
    }

    val visitedPosts = mutableMapOf<Pair<AccountType, MicroBlogKey>, UiTimelineV2.TimelinePostItem>()
    val parentKeys = mutableMapOf<Pair<AccountType, MicroBlogKey>, Pair<AccountType, MicroBlogKey>>()
    val ancestorKeys = mutableSetOf<Pair<AccountType, MicroBlogKey>>()

    fun UiTimelineV2.TimelinePostItem.directParentKey(): Pair<AccountType, MicroBlogKey>? =
        presentation
            .inlineParents
            .lastOrNull()
            ?.let { it.key() }
            ?.takeIf { it in rootPosts }
            ?: post
                .references
                .firstOrNull { it.type == ReferenceType.Reply }
                ?.let { post.accountType to it.statusKey }
                ?.takeIf { it in rootPosts }

    // Resolve links and break cycles before expanding only the posts that remain visible.
    forEach { item ->
        var current = item.asTimelinePostItem() ?: return@forEach
        val activeKeys = mutableSetOf<Pair<AccountType, MicroBlogKey>>()
        while (current.key() !in visitedPosts) {
            val currentKey = current.key()
            visitedPosts[currentKey] = current
            activeKeys += currentKey
            val directParentKey = current.directParentKey()
            val directParent =
                directParentKey
                    ?.takeUnless { it in activeKeys }
                    ?.let { rootPosts.getValue(it) }
            if (directParent == null || directParent.accountType != current.accountType) {
                if (directParentKey in activeKeys) {
                    visitedPosts[currentKey] =
                        current.copy(
                            presentation =
                                current.presentation.copy(
                                    inlineParents =
                                        current.presentation.inlineParents
                                            .dropLast(1)
                                            .toImmutableList(),
                                ),
                        )
                }
                break
            }
            parentKeys[currentKey] = directParent.key()
            ancestorKeys += directParent.key()
            current = directParent
        }
    }

    return mapNotNull { item ->
        val key = item.asTimelinePostItem()?.key() ?: return@mapNotNull item
        if (key in ancestorKeys) {
            return@mapNotNull null
        }
        val post = visitedPosts.getValue(key)
        if (key !in parentKeys) {
            return@mapNotNull post
        }
        val chain =
            generateSequence(key) { parentKeys[it] }
                .map { visitedPosts.getValue(it) }
                .toList()
        val inlineParents =
            buildList {
                chain.forEachIndexed { index, ancestor ->
                    addAll(
                        if (index == chain.lastIndex) {
                            ancestor.presentation.inlineParents
                        } else {
                            ancestor.presentation.inlineParents.dropLast(1)
                        },
                    )
                }
                for (index in chain.lastIndex downTo 1) {
                    val ancestor = chain[index]
                    add(ancestor.copy(presentation = ancestor.presentation.copy(inlineParents = persistentListOf())))
                }
            }.distinctBy { it.statusKey }
                .toImmutableList()
        post.copy(presentation = post.presentation.copy(inlineParents = inlineParents))
    }
}
