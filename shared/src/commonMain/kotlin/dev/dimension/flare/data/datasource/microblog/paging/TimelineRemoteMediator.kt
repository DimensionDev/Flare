package dev.dimension.flare.data.datasource.microblog.paging

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import dev.dimension.flare.common.SnowflakeIdGenerator
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.saveToDatabase
import dev.dimension.flare.data.database.cache.model.DbPagingKey
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.database.cache.model.DbStatusWithReference
import dev.dimension.flare.data.translation.NoopPreTranslationService
import dev.dimension.flare.data.translation.PreTranslationService
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.collections.immutable.toImmutableList

@OptIn(ExperimentalPagingApi::class)
internal class TimelineRemoteMediator(
    private val loader: CacheableRemoteLoader<UiTimelineV2>,
    private val database: CacheDatabase,
    private val allowLongText: Boolean,
    private val notifyError: (Throwable) -> Unit = {},
    private val preTranslationService: PreTranslationService = NoopPreTranslationService,
) : BasePagingRemoteMediator<DbStatusWithReference, DbPagingTimelineWithStatus>(
        database = database,
    ),
    RemoteLoader<DbPagingTimelineWithStatus> {
    override val pagingKey: String
        get() = loader.pagingKey

    private var prependCapabilityState =
        when {
            !loader.supportPrepend -> PrependCapabilityState.Unsupported
            loader.refreshBehavior == RefreshBehavior.MergeTop -> PrependCapabilityState.Unknown
            else -> PrependCapabilityState.Supported
        }
    private var lastSaveMode: SaveMode = SaveMode.Replace

    init {
        if (loader is ReportableRemoteLoader) {
            loader.reportError = notifyError
        }
    }

    override fun onError(e: Throwable) {
        notifyError(e)
    }

    override suspend fun initialize(): InitializeAction =
        if (loader.supportPrepend) {
            if (database.pagingTimelineDao().anyPaging(loader.pagingKey)) {
                InitializeAction.SKIP_INITIAL_REFRESH
            } else {
                InitializeAction.LAUNCH_INITIAL_REFRESH
            }
        } else {
            InitializeAction.LAUNCH_INITIAL_REFRESH
        }

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<DbPagingTimelineWithStatus> {
        val result =
            when (request) {
                PagingRequest.Refresh -> refreshTimeline(pageSize)
                is PagingRequest.Prepend -> prependTimeline(pageSize, request.previousKey)
                is PagingRequest.Append -> {
                    lastSaveMode = SaveMode.AppendBottom
                    timeline(
                        pageSize = pageSize,
                        request = request,
                    )
                }
            }
        val data =
            result.data.map {
                TimelinePagingMapper.toDb(
                    data = it,
                    pagingKey = pagingKey,
                )
            }
        return PagingResult(
            data = data,
            nextKey = result.nextKey,
            previousKey = result.previousKey,
        )
    }

    override suspend fun updatePagingKeys(
        loadType: LoadType,
        request: PagingRequest,
        result: PagingResult<DbPagingTimelineWithStatus>,
    ) {
        when {
            loadType == LoadType.REFRESH && lastSaveMode == SaveMode.MergeTop -> {
                val existing = database.pagingTimelineDao().getPagingKey(pagingKey)
                if (existing == null) {
                    database.pagingTimelineDao().insertPagingKey(
                        DbPagingKey(
                            pagingKey = pagingKey,
                            nextKey = result.nextKey,
                            prevKey = result.previousKey,
                        ),
                    )
                } else {
                    result.previousKey?.let {
                        database.pagingTimelineDao().updatePagingKeyPrevKey(
                            pagingKey = pagingKey,
                            prevKey = it,
                        )
                    }
                }
            }

            else -> super.updatePagingKeys(loadType, request, result)
        }
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
                    data = result.data.collapseReplyChains(),
                )
            }

    override suspend fun onSaveCache(
        request: PagingRequest,
        data: List<DbPagingTimelineWithStatus>,
    ) {
        if (lastSaveMode == SaveMode.Replace && request is PagingRequest.Refresh) {
            data.groupBy { it.timeline.pagingKey }.keys.forEach { key ->
                database
                    .pagingTimelineDao()
                    .delete(pagingKey = key)
            }
        }
        if (lastSaveMode == SaveMode.MergeTop && data.isNotEmpty()) {
            // load current timeline caches
            val currentCaches =
                database
                    .pagingTimelineDao()
                    .getByPagingKey(pagingKey)
                    .map {
                        it.copy(
                            sortId = SnowflakeIdGenerator.nextId(),
                        )
                    }
            database.pagingTimelineDao().insertAll(
                currentCaches,
            )
        }
        saveToDatabase(database, data)
        preTranslationService.enqueueStatuses(
            data
                .flatMap { item ->
                    listOfNotNull(item.status.status.data) +
                        item.status.references.mapNotNull { it.status?.data }
                }.distinctBy { it.id },
            allowLongText = allowLongText,
        )
    }

    private suspend fun refreshTimeline(pageSize: Int): PagingResult<UiTimelineV2> {
        val existingPagingKey = database.pagingTimelineDao().getPagingKey(pagingKey)
        if (loader.refreshBehavior != RefreshBehavior.MergeTop || existingPagingKey?.prevKey == null) {
            lastSaveMode = SaveMode.Replace
            return timeline(
                pageSize = pageSize,
                request = PagingRequest.Refresh,
            )
        }
        val mergeResult = runTopMerge(pageSize = pageSize, startKey = existingPagingKey.prevKey)
        return when (mergeResult) {
            is TopMergeResult.Success -> {
                lastSaveMode = SaveMode.MergeTop
                prependCapabilityState = PrependCapabilityState.Supported
                mergeResult.result
            }

            TopMergeResult.Failure -> {
                prependCapabilityState = PrependCapabilityState.Unsupported
                lastSaveMode = SaveMode.Replace
                timeline(
                    pageSize = pageSize,
                    request = PagingRequest.Refresh,
                )
            }
        }
    }

    private suspend fun prependTimeline(
        pageSize: Int,
        previousKey: String,
    ): PagingResult<UiTimelineV2> {
        if (!loader.supportPrepend || prependCapabilityState != PrependCapabilityState.Supported) {
            lastSaveMode = SaveMode.MergeTop
            return PagingResult(endOfPaginationReached = true)
        }
        return when (val mergeResult = runTopMerge(pageSize = pageSize, startKey = previousKey)) {
            is TopMergeResult.Success -> {
                lastSaveMode = SaveMode.MergeTop
                mergeResult.result
            }

            TopMergeResult.Failure -> {
                prependCapabilityState = PrependCapabilityState.Unsupported
                lastSaveMode = SaveMode.MergeTop
                PagingResult(endOfPaginationReached = true)
            }
        }
    }

    private suspend fun runTopMerge(
        pageSize: Int,
        startKey: String,
    ): TopMergeResult {
        val existingKeys =
            database
                .pagingTimelineDao()
                .getByPagingKey(pagingKey)
                .mapTo(mutableSetOf()) { it.statusKey.toString() }
        val merged = mutableListOf<UiTimelineV2>()
        val seenNewKeys = mutableSetOf<String>()
        var currentKey = startKey
        var newTopKey: String? = null
        val mergePageSize = pageSize.coerceAtLeast(MIN_TOP_MERGE_PAGE_SIZE).coerceAtMost(MAX_TOP_MERGE_PAGE_SIZE)
        repeat(MAX_TOP_MERGE_PAGES) {
            val response =
                timeline(
                    pageSize = mergePageSize,
                    request = PagingRequest.Prepend(currentKey),
                )
            if (!response.data.isSortedDescendingByCreatedAt()) {
                return TopMergeResult.Failure
            }
            val filtered =
                response.data.filterNot { item ->
                    val key = item.toStableTimelineKey()
                    item.statusKey.id == currentKey || item.statusKey.toString() in existingKeys || !seenNewKeys.add(key)
                }
            if (response.data.isNotEmpty() && filtered.isEmpty()) {
                return TopMergeResult.Failure
            }
            if (newTopKey == null) {
                newTopKey = filtered.firstOrNull()?.statusKey?.id
            }
            merged += filtered
            if (
                response.previousKey == null ||
                response.previousKey == currentKey ||
                response.data.size < mergePageSize ||
                merged.size >= MAX_TOP_MERGE_ITEMS
            ) {
                return TopMergeResult.Success(
                    PagingResult(
                        data = merged,
                        nextKey = response.nextKey,
                        previousKey = newTopKey ?: startKey,
                        endOfPaginationReached = response.previousKey == null,
                    ),
                )
            }
            currentKey = response.previousKey
        }
        return if (merged.isEmpty()) {
            TopMergeResult.Failure
        } else {
            TopMergeResult.Success(
                PagingResult(
                    data = merged,
                    previousKey = newTopKey ?: startKey,
                ),
            )
        }
    }
}

private const val MIN_TOP_MERGE_PAGE_SIZE = 60
private const val MAX_TOP_MERGE_PAGE_SIZE = 80
private const val MAX_TOP_MERGE_PAGES = 5
private const val MAX_TOP_MERGE_ITEMS = 200

private enum class SaveMode {
    Replace,
    MergeTop,
    AppendBottom,
}

private enum class PrependCapabilityState {
    Unknown,
    Supported,
    Unsupported,
}

private sealed interface TopMergeResult {
    data class Success(
        val result: PagingResult<UiTimelineV2>,
    ) : TopMergeResult

    data object Failure : TopMergeResult
}

private fun UiTimelineV2.toStableTimelineKey(): String = itemKey ?: "${accountType}_$statusKey"

private fun List<UiTimelineV2>.isSortedDescendingByCreatedAt(): Boolean =
    zipWithNext().all { (left, right) ->
        left.createdAt.value >= right.createdAt.value
    }

private fun List<UiTimelineV2>.collapseReplyChains(): List<UiTimelineV2> {
    val rootPosts =
        asSequence()
            .filterIsInstance<UiTimelineV2.Post>()
            .associateBy { it.accountType to it.statusKey }
    if (rootPosts.isEmpty()) {
        return this
    }

    val collapsedPosts = mutableMapOf<Pair<AccountType, MicroBlogKey>, UiTimelineV2.Post>()
    val ancestorKeys = mutableSetOf<Pair<AccountType, MicroBlogKey>>()

    fun collapse(post: UiTimelineV2.Post): UiTimelineV2.Post {
        val key = post.accountType to post.statusKey
        collapsedPosts[key]?.let {
            return it
        }

        val directParent =
            post.parents
                .lastOrNull()
                ?.takeIf { rootPosts.containsKey(it.accountType to it.statusKey) }
                ?.let { rootPosts.getValue(it.accountType to it.statusKey) }

        val collapsed =
            if (directParent == null || directParent.accountType != post.accountType) {
                post
            } else {
                ancestorKeys += directParent.accountType to directParent.statusKey
                val collapsedParent = collapse(directParent)
                post.copy(
                    parents =
                        (
                            collapsedParent.parents +
                                listOf(collapsedParent) +
                                post.parents.dropLast(1)
                        ).distinctBy { it.statusKey }
                            .toImmutableList(),
                )
            }
        collapsedPosts[key] = collapsed
        return collapsed
    }

    return mapNotNull { item ->
        if (item !is UiTimelineV2.Post) {
            item
        } else {
            val key = item.accountType to item.statusKey
            collapse(item).takeUnless { key in ancestorKeys }
        }
    }
}
