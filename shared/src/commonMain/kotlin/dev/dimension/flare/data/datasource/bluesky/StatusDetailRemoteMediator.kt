package dev.dimension.flare.data.datasource.bluesky

import androidx.paging.ExperimentalPagingApi
import app.bsky.feed.FeedViewPost
import app.bsky.feed.GetPostThreadQueryParams
import app.bsky.feed.GetPostThreadResponseThreadUnion
import app.bsky.feed.GetPostsQueryParams
import app.bsky.feed.ReplyRef
import app.bsky.feed.ReplyRefParentUnion
import app.bsky.feed.ReplyRefRootUnion
import app.bsky.feed.ThreadViewPost
import app.bsky.feed.ThreadViewPostParentUnion
import app.bsky.feed.ThreadViewPostReplieUnion
import dev.dimension.flare.common.BaseTimelineRemoteMediator
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.toDbPagingTimeline
import dev.dimension.flare.data.database.cache.model.DbPagingTimeline
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
        pageSize: Int,
        request: Request,
    ): Result {
        val result =
            when (request) {
                is Request.Append -> {
                    if (statusOnly) {
                        return Result(
                            endOfPaginationReached = true,
                        )
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
                                val parents = mutableListOf<ThreadViewPost>()
                                var current: ThreadViewPost? = thread.value
                                while (current != null) {
                                    parents.add(current)
                                    current =
                                        when (val parent = current.parent) {
                                            is ThreadViewPostParentUnion.ThreadViewPost -> parent.value
                                            else -> null
                                        }
                                }
                                val replies =
                                    thread.value.replies.mapNotNull {
                                        when (it) {
                                            is ThreadViewPostReplieUnion.ThreadViewPost -> {
                                                if (it.value.replies.any()) {
                                                    val last =
                                                        it.value.replies.last().let {
                                                            when (it) {
                                                                is ThreadViewPostReplieUnion.ThreadViewPost -> it.value.post
                                                                else -> null
                                                            }
                                                        }
                                                    if (last != null) {
                                                        val parents =
                                                            listOfNotNull(it.value.post) +
                                                                it.value.replies.toList().dropLast(1).mapNotNull {
                                                                    when (it) {
                                                                        is ThreadViewPostReplieUnion.ThreadViewPost -> it.value.post
                                                                        else -> null
                                                                    }
                                                                }
                                                        val currentRef =
                                                            ReplyRef(
                                                                root = ReplyRefRootUnion.PostView(parents.last()),
                                                                parent = ReplyRefParentUnion.PostView(parents.last()),
                                                            )

                                                        FeedViewPost(
                                                            post = last,
                                                            reply = currentRef,
                                                        )
                                                    } else {
                                                        FeedViewPost(
                                                            it.value.post,
                                                        )
                                                    }
                                                } else {
                                                    FeedViewPost(
                                                        it.value.post,
                                                    )
                                                }
                                            }
                                            else -> null
                                        }
                                    }
                                parents.map { FeedViewPost(it.post) }.reversed() + FeedViewPost(thread.value.post) + replies
                            }

                            else -> emptyList()
                        }
                    }
                }
                is Request.Prepend -> {
                    return Result(
                        endOfPaginationReached = true,
                    )
                }
                Request.Refresh -> {
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

                    val current =
                        service
                            .getPosts(
                                GetPostsQueryParams(
                                    persistentListOf(AtUri(statusKey.id)),
                                ),
                            ).requireResponse()
                            .posts
                            .firstOrNull()
                    listOfNotNull(current).map(::FeedViewPost)
                }
            }

        val shouldLoadMore = !(request is Request.Append || statusOnly)
        return Result(
            endOfPaginationReached = !shouldLoadMore,
            data =
                result.toDbPagingTimeline(
                    accountKey,
                    pagingKey,
                ) {
                    -result.indexOf(it).toLong()
                },
            nextKey = if (shouldLoadMore) pagingKey else null,
        )
    }
}
