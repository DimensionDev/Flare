package dev.dimension.flare.data.datasource.microblog.paging

import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.common.SnowflakeIdGenerator
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.saveToDatabase
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.collections.immutable.toImmutableList

@OptIn(ExperimentalPagingApi::class)
internal class TimelineRemoteMediator(
    private val loader: CacheableRemoteLoader<UiTimelineV2>,
    private val database: CacheDatabase,
    private val notifyError: (Throwable) -> Unit = {},
) : BasePagingRemoteMediator<DbPagingTimelineWithStatus, DbPagingTimelineWithStatus>(
        database = database,
    ),
    RemoteLoader<DbPagingTimelineWithStatus> {
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

    override suspend fun initialize(): InitializeAction =
        if (loader.supportPrepend) {
            InitializeAction.SKIP_INITIAL_REFRESH
        } else {
            InitializeAction.LAUNCH_INITIAL_REFRESH
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
        if (request is PagingRequest.Refresh) {
            data.groupBy { it.timeline.pagingKey }.keys.forEach { key ->
                database
                    .pagingTimelineDao()
                    .delete(pagingKey = key)
            }
        }
        if (request is PagingRequest.Prepend && loader.supportPrepend) {
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
    }
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
