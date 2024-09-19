package dev.dimension.flare.data.datasource.bluesky

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import app.bsky.feed.GetPostThreadQueryParams
import app.bsky.feed.GetPostThreadResponseThreadUnion
import app.bsky.feed.GetPostsQueryParams
import app.bsky.feed.ThreadViewPostParentUnion
import app.bsky.feed.ThreadViewPostReplieUnion
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.Bluesky
import dev.dimension.flare.data.database.cache.model.DbPagingTimeline
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.network.bluesky.BlueskyService
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.firstOrNull
import sh.christian.ozone.api.AtUri
import kotlin.uuid.Uuid

@OptIn(ExperimentalPagingApi::class)
internal class StatusDetailRemoteMediator(
    private val statusKey: MicroBlogKey,
    private val service: BlueskyService,
    private val accountKey: MicroBlogKey,
    private val database: CacheDatabase,
    private val pagingKey: String,
    private val statusOnly: Boolean,
) : RemoteMediator<Int, DbPagingTimelineWithStatus>() {
    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, DbPagingTimelineWithStatus>,
    ): MediatorResult {
        return try {
            if (loadType != LoadType.REFRESH) {
                return MediatorResult.Success(
                    endOfPaginationReached = true,
                )
            }
            if (!database.pagingTimelineDao().existsPaging(accountKey, pagingKey)) {
                database.statusDao().get(statusKey, accountKey).firstOrNull()?.let {
                    database
                        .pagingTimelineDao()
                        .insertAll(
                            listOf(
                                DbPagingTimeline(
                                    accountKey = accountKey,
                                    statusKey = statusKey,
                                    pagingKey = pagingKey,
                                    sortId = 0,
                                    _id = Uuid.random().toString(),
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
            Bluesky.savePost(
                accountKey,
                pagingKey,
                database,
                result,
            ) {
                -result.indexOf(it).toLong()
            }
            MediatorResult.Success(
                endOfPaginationReached = true,
            )
        } catch (e: Throwable) {
            MediatorResult.Error(e)
        }
    }
}
