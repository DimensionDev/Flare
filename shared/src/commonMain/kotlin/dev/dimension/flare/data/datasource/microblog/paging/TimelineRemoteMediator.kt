package dev.dimension.flare.data.datasource.microblog.paging

import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.common.SnowflakeIdGenerator
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.saveToDatabase
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
) : BasePagingRemoteMediator<
        OffsetFromStartPagingKey,
        DbStatusWithReference,
        DbPagingTimelineWithStatus,
    >(
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
            timeline(
                pageSize = pageSize,
                request = request,
            )
        val data =
            result.data.map {
                TimelinePagingMapper.toDb(
                    data = it,
                    pagingKey = pagingKey,
                    sortId = (loader as? SortIdProvider)?.sortId(it),
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
            data
                .groupBy { it.timeline.pagingKey }
                .keys
                .plus(loader.pagingKey)
                .distinct()
                .forEach { key ->
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
        preTranslationService.enqueueStatuses(
            data
                .flatMap { item ->
                    listOfNotNull(item.status.status.data) +
                        item.status.references.mapNotNull { it.status?.data }
                }.distinctBy { it.id },
            allowLongText = allowLongText,
        )
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

    fun UiTimelineV2.Post.key(): Pair<AccountType, MicroBlogKey> = accountType to statusKey

    fun UiTimelineV2.Post.directParentKey(): Pair<AccountType, MicroBlogKey>? =
        parents
            .lastOrNull()
            ?.let { it.key() }
            ?.takeIf { rootPosts.containsKey(it) }

    fun collapse(start: UiTimelineV2.Post): UiTimelineV2.Post {
        val startKey = start.key()
        collapsedPosts[startKey]?.let {
            return it
        }

        val path = mutableListOf<UiTimelineV2.Post>()
        val activeKeys = mutableSetOf<Pair<AccountType, MicroBlogKey>>()
        var current = start
        var collapsed: UiTimelineV2.Post

        while (true) {
            val currentKey = current.key()
            collapsedPosts[currentKey]?.let {
                collapsed = it
                break
            }

            activeKeys += currentKey
            val directParentKey = current.directParentKey()
            val directParent =
                directParentKey
                    ?.takeUnless { it in activeKeys }
                    ?.let { rootPosts.getValue(it) }

            if (directParent == null || directParent.accountType != current.accountType) {
                collapsed =
                    if (directParentKey in activeKeys) {
                        current.copy(
                            parents = current.parents.dropLast(1).toImmutableList(),
                        )
                    } else {
                        current
                    }
                collapsedPosts[currentKey] = collapsed
                break
            }

            ancestorKeys += directParent.key()
            path += current
            current = directParent
        }

        for (post in path.asReversed()) {
            collapsed =
                post.copy(
                    parents =
                        (
                            collapsed.parents +
                                listOf(collapsed) +
                                post.parents.dropLast(1)
                        ).distinctBy { it.statusKey }
                            .toImmutableList(),
                )
            collapsedPosts[post.key()] = collapsed
        }
        return collapsedPosts.getValue(startKey)
    }

    return mapNotNull { item ->
        if (item !is UiTimelineV2.Post) {
            item
        } else {
            val key = item.key()
            collapse(item).takeUnless { key in ancestorKeys }
        }
    }
}
