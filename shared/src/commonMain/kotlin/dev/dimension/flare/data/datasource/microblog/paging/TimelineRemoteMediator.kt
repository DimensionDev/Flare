package dev.dimension.flare.data.datasource.microblog.paging

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator.MediatorResult
import dev.dimension.flare.common.SnowflakeIdGenerator
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.saveToDatabase
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.translation.NoopPreTranslationService
import dev.dimension.flare.data.translation.PreTranslationService
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.ReferenceType
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.asTimelinePostItem
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

@OptIn(ExperimentalPagingApi::class)
internal open class TimelineRemoteMediator(
    private val loader: CacheableRemoteLoader<UiTimelineV2>,
    private val database: CacheDatabase,
    private val allowLongText: Boolean,
    private val notifyError: (Throwable) -> Unit = {},
    private val preTranslationService: PreTranslationService = NoopPreTranslationService,
    private val refreshOnInitialize: suspend () -> Boolean = { true },
    private val isRequestValid: suspend () -> Boolean = { true },
) : BasePagingRemoteMediator<
        OffsetFromStartPagingKey,
        TimelinePageItem,
        DbPagingTimelineWithStatus,
    >(
        database = database,
    ),
    RemoteLoader<DbPagingTimelineWithStatus> {
    private var suppressInitialPrepend = false

    override val pagingKey: String
        get() = loader.pagingKey

    override suspend fun canSave(): Boolean = isRequestValid()

    init {
        if (loader is ReportableRemoteLoader) {
            loader.reportError = notifyError
        }
    }

    override fun onError(e: Throwable) {
        notifyError(e)
    }

    override suspend fun initialize(): InitializeAction {
        val hasCache = database.pagingTimelineDao().anyPaging(loader.pagingKey)
        if (!hasCache) {
            return InitializeAction.LAUNCH_INITIAL_REFRESH
        }

        val shouldRefresh = refreshOnInitialize()
        suppressInitialPrepend = loader.supportPrepend && !shouldRefresh
        return if (!shouldRefresh || loader.supportPrepend) {
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
        return super.doLoad(loadType, state)
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
                (retainedTimelineIds.keys + loader.pagingKey).flatMap { key ->
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

private fun List<UiTimelineV2>.collapseReplyChains(): List<UiTimelineV2> {
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
