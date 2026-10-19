package dev.dimension.flare.data.datasource.microblog.paging

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator.MediatorResult
import androidx.room3.withReadTransaction
import dev.dimension.flare.common.SnowflakeIdGenerator
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.connect
import dev.dimension.flare.data.database.cache.mapper.saveToDatabase
import dev.dimension.flare.data.database.cache.model.DbPagingKey
import dev.dimension.flare.data.database.cache.model.DbPagingTimeline
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.datasource.microblog.MixedRemoteMediator
import dev.dimension.flare.data.translation.NoopPreTranslationService
import dev.dimension.flare.data.translation.PreTranslationService
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.ReferenceType
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.asTimelinePostItem
import dev.dimension.flare.ui.render.toUi
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
    private val rememberPosition: Boolean? = null,
    private val accountKeys: Set<MicroBlogKey> = emptySet(),
) : BasePagingRemoteMediator<
        OffsetFromStartPagingKey,
        TimelinePageItem,
        DbPagingTimelineWithStatus,
    >(
        database = database,
    ),
    RemoteLoader<DbPagingTimelineWithStatus> {
    private var suppressInitialPrepend = false
    private var generation: String? = null
    private val ordersByTime = (loader as? MixedRemoteMediator)?.pageLoader()?.ordersByTime == true

    override val pagingKey: String
        get() = if (rememberPosition != null) "home:${loader.pagingKey}" else loader.pagingKey

    init {
        if (loader is ReportableRemoteLoader) {
            loader.reportError = notifyError
        }
    }

    override fun onError(e: Throwable) {
        notifyError(e)
    }

    override suspend fun initialize(): InitializeAction {
        prepareHomeTimeline()
        val hasCache = database.pagingTimelineDao().anyPaging(pagingKey)
        if (!hasCache) {
            return InitializeAction.LAUNCH_INITIAL_REFRESH
        }

        val shouldRefresh = refreshOnInitialize()
        suppressInitialPrepend = loader.supportPrepend && !shouldRefresh
        return if (!shouldRefresh || (loader.supportPrepend && rememberPosition != true)) {
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
        }
        return if (rememberPosition != null) loadHomePage(loadType, state.config.pageSize) else super.doLoad(loadType, state)
    }

    internal suspend fun prepareHomeTimeline() {
        if (rememberPosition == null || generation != null) return
        val accounts = Json.encodeToString(accountKeys)
        val prepared =
            database.withReadTransaction {
                val dao = database.pagingTimelineDao()
                val mode = dao.getPagingKey("home:mode")
                dao.getPagingKey(pagingKey)?.takeIf {
                    mode?.rememberPosition == rememberPosition && it.rememberPosition == rememberPosition &&
                        Json.decodeFromString<Set<MicroBlogKey>>(it.accountKeys) == accountKeys
                }
            }
        if (prepared != null) {
            generation = prepared.generation
            return
        }
        database.connect {
            currentCoroutineContext().ensureActive()
            val dao = database.pagingTimelineDao()
            val mode = dao.getPagingKey("home:mode")
            if (mode?.rememberPosition != rememberPosition) {
                dao.clearHomeGaps()
                dao.clearHomePresentationReferences()
                dao.clearHomeRows()
                dao.clearHomeKeys()
                dao.clearPositions()
                dao.insertPagingKey(DbPagingKey("home:mode", rememberPosition = rememberPosition))
            }
            val previous = dao.getPagingKey(pagingKey)
            val key =
                if (previous == null || previous.rememberPosition != rememberPosition) {
                    // A mode change invalidates outstanding responses before rebuilding this cache.
                    dao.deletePresentationReferences(pagingKey)
                    dao.delete(pagingKey)
                    dao.deletePositions(pagingKey)
                    DbPagingKey(pagingKey, rememberPosition = rememberPosition)
                        .also { dao.insertPagingKey(it) }
                } else {
                    previous
                }
            if (key.accountKeys != accounts) dao.insertPagingKey(key.copy(accountKeys = accounts))
            generation = key.generation
        }
    }

    private suspend fun loadHomePage(
        loadType: LoadType,
        pageSize: Int,
    ): MediatorResult {
        prepareHomeTimeline()
        val dao = database.pagingTimelineDao()
        val before =
            dao
                .getPagingKey(pagingKey)
                ?.takeIf { it.generation == generation } ?: return MediatorResult.Success(true)
        if (loadType == LoadType.PREPEND && rememberPosition == true) return MediatorResult.Success(true)
        val request =
            when (loadType) {
                LoadType.REFRESH -> PagingRequest.Refresh
                LoadType.APPEND -> PagingRequest.Append(before.nextKey ?: return MediatorResult.Success(true))
                LoadType.PREPEND -> PagingRequest.Prepend(before.prevKey ?: return MediatorResult.Success(true))
            }
        val boundary =
            if (loadType == LoadType.REFRESH && rememberPosition == true) {
                Json.decodeFromString<Map<String, List<String>>>(before.sourceHeads)
            } else {
                emptyMap()
            }
        val result = fetchPage(pageSize, request, boundary)
        val mapped =
            TimelinePagingMapper.toDb(
                result.data,
                pagingKey,
                sortIds = result.data.map { item -> homeSortId(item) },
            )
        database.connect {
            currentCoroutineContext().ensureActive()
            if (dao.getPagingKey(pagingKey) != before) return@connect
            val first = dao.getFirstRow(pagingKey)
            if (rememberPosition == true && loadType == LoadType.APPEND && result.nextKey != null) {
                val existing = existingRows(mapped)
                check(
                    continuationIdentity(result.nextKey) != continuationIdentity(before.nextKey) ||
                        mapped.any { it.timeline._id !in existing },
                ) {
                    "Timeline continuation did not advance"
                }
            }
            if (rememberPosition == true) {
                val existing = existingRows(mapped)
                val hasNewHead =
                    mapped.any {
                        it.timeline._id !in existing && (!ordersByTime || first == null || it.timeline.sortId < first.sortId)
                    }
                val gap =
                    if (loadType == LoadType.REFRESH && first != null && hasNewHead && result.nextKey != null) {
                        UiTimelineV2.Gap(
                            statusKey = MicroBlogKey(Uuid.random().toString(), "timeline-gap"),
                            createdAt = Clock.System.now().toUi(),
                            nextKey = result.nextKey,
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
                    savePreservingOrder(
                        mapped + gapRows,
                        beforeId = first?.takeIf { loadType == LoadType.REFRESH }?._id,
                    )
                }
            } else {
                onSaveCache(request, mapped)
            }
            dao.insertPagingKey(
                before.copy(
                    nextKey =
                        if (loadType == LoadType.REFRESH && rememberPosition == true && first != null) {
                            before.nextKey
                        } else if (loadType == LoadType.PREPEND) {
                            before.nextKey
                        } else {
                            result.nextKey
                        },
                    prevKey = if (loadType == LoadType.APPEND) before.prevKey else result.previousKey,
                    revision = before.revision + 1,
                    sourceHeads =
                        if (loadType ==
                            LoadType.REFRESH
                        ) {
                            Json.encodeToString(boundary + result.sourceHeads)
                        } else {
                            before.sourceHeads
                        },
                ),
            )
            enqueuePreTranslation(mapped)
        }
        result.sourceFailures.forEach(notifyError)
        return MediatorResult.Success(if (loadType == LoadType.REFRESH) false else result.nextKey == null)
    }

    /** Loads exactly one logical page; the gap itself is the compare-and-set token. */
    internal suspend fun loadGap(
        gap: UiTimelineV2.Gap,
        pageSize: Int = 20,
    ): GapLoadResult {
        check(rememberPosition == true)
        prepareHomeTimeline()
        val dao = database.pagingTimelineDao()
        val key = dao.getPagingKey(pagingKey)?.takeIf { it.generation == generation } ?: return GapLoadResult()
        val gapRow = TimelinePagingMapper.toDb(listOf(gap), pagingKey).single().timeline
        if (dao.getRow(pagingKey, gapRow._id) == null) return GapLoadResult()
        val result =
            try {
                fetchPage(pageSize, PagingRequest.Append(gap.nextKey), gap.lowerBound)
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
            if (dao.getPagingKey(pagingKey)?.generation != key.generation) return@connect
            val row = dao.getRow(pagingKey, gapRow._id) ?: return@connect
            val current =
                database
                    .statusDao()
                    .getWithReferencesSync(gap.statusKey, AccountType.Guest)
                    ?.status
                    ?.data
                    ?.content as? UiTimelineV2.Gap ?: return@connect
            if (current.revision != gap.revision || current.nextKey != gap.nextKey) return@connect
            val existing = existingRows(mapped)
            check(
                continuationIdentity(result.nextKey) != continuationIdentity(gap.nextKey) || mapped.any { it.timeline._id !in existing },
            ) {
                "Timeline continuation did not advance"
            }
            if (ordersByTime) saveToDatabase(database, mapped) else savePreservingOrder(mapped, row._id)
            insertedKeys = mapped.filter { it.timeline._id !in existing }.map { "${pagingKey}_${it.timeline.statusId}" }.distinct()
            if (result.nextKey == null) {
                dao.delete(listOf(row))
                database.statusDao().delete(gap.statusKey, AccountType.Guest)
            } else {
                val next = gap.copy(nextKey = result.nextKey, revision = gap.revision + 1)
                val nextRow = TimelinePagingMapper.toDb(listOf(next), pagingKey).single()
                saveToDatabase(
                    database,
                    listOf(nextRow.copy(timeline = nextRow.timeline.copy(sortId = checkNotNull(dao.getRow(pagingKey, row._id)).sortId))),
                )
            }
            enqueuePreTranslation(mapped)
        }
        result.sourceFailures.forEach(notifyError)
        return GapLoadResult(insertedKeys, result.continuationFailure)
    }

    private suspend fun fetchPage(
        pageSize: Int,
        request: PagingRequest,
        boundary: Map<String, List<String>>,
    ): PagingResult<UiTimelineV2> {
        val result =
            if (loader is MixedRemoteMediator) {
                loader.pageLoader().loadPage(pageSize, request) { source, items ->
                    items.any { it.stableItemKey in boundary[source].orEmpty() }
                }
            } else {
                loader.load(pageSize, request).let { response ->
                    val join =
                        if (loader.canJoinTimeline) {
                            response.data.indexOfFirst { it.stableItemKey in boundary[loader.pagingKey].orEmpty() }
                        } else {
                            -1
                        }
                    val data = if (join >= 0 && request is PagingRequest.Append) response.data.take(join + 1) else response.data
                    response.copy(
                        data = data,
                        nextKey = if (join >= 0) null else response.nextKey,
                        sourceHeads = mapOf(loader.pagingKey to data.map { it.stableItemKey }),
                    )
                }
            }
        return if (loader.collapseReplyChains) result.copy(data = result.data.collapseReplyChains()) else result
    }

    private fun continuationIdentity(cursor: String?): String? =
        cursor?.let { if (loader is MixedRemoteMediator) loader.pageLoader().continuationIdentity(it) else it }

    private suspend fun homeSortId(item: UiTimelineV2): Long? =
        if (rememberPosition == true && !ordersByTime) {
            null
        } else {
            (loader as? SortIdProvider)?.sortId(item)?.let { if (rememberPosition == true) it * 2 else it }
        }

    private suspend fun existingRows(data: List<DbPagingTimelineWithStatus>): Map<String, DbPagingTimeline> =
        data
            .map { it.timeline.statusId }
            .distinct()
            .chunked(500)
            .flatMap {
                database.pagingTimelineDao().getByPagingKeyAndStatusIds(pagingKey, it)
            }.associateBy { it._id }

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
            dao.refreshPositionRanks(pagingKey)
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
        val sortIdProvider = loader as? SortIdProvider
        val data =
            TimelinePagingMapper.toDb(
                data = result.data,
                pagingKey = pagingKey,
                sortIds = result.data.map { sortIdProvider?.sortId(it) },
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
