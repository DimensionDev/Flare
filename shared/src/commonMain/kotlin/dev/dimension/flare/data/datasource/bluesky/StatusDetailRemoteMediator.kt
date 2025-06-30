package dev.dimension.flare.data.datasource.bluesky

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import app.bsky.feed.GetPostThreadQueryParams
import app.bsky.feed.GetPostThreadResponseThreadUnion
import app.bsky.feed.GetPostsQueryParams
import app.bsky.feed.ThreadViewPostParentUnion
import app.bsky.feed.ThreadViewPostReplieUnion
import dev.dimension.flare.common.BaseTimelineRemoteMediator
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.toDb
import dev.dimension.flare.data.database.cache.model.DbPagingTimeline
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.network.bluesky.BlueskyService
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.firstOrNull
import sh.christian.ozone.api.AtUri

@OptIn(ExperimentalPagingApi::class)
internal class StatusDetailRemoteMediator(
    private val statusKey: MicroBlogKey,
    private val service: BlueskyService,
    private val accountKey: MicroBlogKey,
    private val database: CacheDatabase,
    private val statusOnly: Boolean,
) : BaseTimelineRemoteMediator(
        database = database,
        accountType = AccountType.Specific(accountKey),
    ) {
    override val pagingKey: String =
        buildString {
            append("status_detail_")
            if (statusOnly) {
                append("status_only_")
            }
            append(statusKey.toString())
            append("_")
            append(accountKey.toString())
        }

    override suspend fun timeline(
        loadType: LoadType,
        state: PagingState<Int, DbPagingTimelineWithStatus>,
    ): Result {
        if (loadType != LoadType.REFRESH) {
            return Result(
                endOfPaginationReached = true,
            )
        }
        if (!database.pagingTimelineDao().existsPaging(accountKey, pagingKey)) {
            database.statusDao().get(statusKey, AccountType.Specific(accountKey)).firstOrNull()?.let {
                database
                    .pagingTimelineDao()
                    .insertAll(
                        listOf(
                            DbPagingTimeline(
                                accountType = AccountType.Specific(accountKey),
                                statusKey = statusKey,
                                pagingKey = pagingKey,
                                sortId = 0,
                            ),
                        ),
                    )
            }
        }
        val result =
            if (statusOnly) {
                val current =
                    service
                        .getPosts(
                            GetPostsQueryParams(
                                persistentListOf(AtUri(statusKey.id)),
                            ),
                        ).requireResponse()
                        .posts
                        .firstOrNull()
                listOfNotNull(current)
            } else {
                val context =
                    service
                        .getPostThread(
                            GetPostThreadQueryParams(
                                AtUri(statusKey.id),
                            ),
                        ).requireResponse()
                when (val thread = context.thread) {
                    is GetPostThreadResponseThreadUnion.ThreadViewPost -> {
                        val parent =
                            when (val value = thread.value.parent) {
                                is ThreadViewPostParentUnion.ThreadViewPost -> value.value
                                else -> null
                            }
                        val replies =
                            thread.value.replies.mapNotNull {
                                when (it) {
                                    is ThreadViewPostReplieUnion.ThreadViewPost -> it.value.post
                                    else -> null
                                }
                            }
                        listOfNotNull(parent?.post) + thread.value.post + replies
                    }

                    else -> emptyList()
                }
            }
        return Result(
            endOfPaginationReached = true,
            data =
                result.toDb(
                    accountKey,
                    pagingKey,
                ) {
                    -result.indexOf(it).toLong()
                },
        )
    }
}
