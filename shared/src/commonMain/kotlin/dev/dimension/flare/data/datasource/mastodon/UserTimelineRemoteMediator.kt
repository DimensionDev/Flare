package dev.dimension.flare.data.datasource.mastodon

import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.network.mastodon.MastodonService
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.mapper.render

@OptIn(ExperimentalPagingApi::class)
internal class UserTimelineRemoteMediator(
    private val service: MastodonService,
    private val accountKey: MicroBlogKey,
    private val userKey: MicroBlogKey,
    private val onlyMedia: Boolean = false,
    private val withReplies: Boolean = false,
    private val withPinned: Boolean = false,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey =
        buildString {
            append("user_timeline")
            if (onlyMedia) {
                append("media")
            }
            if (withReplies) {
                append("replies")
            }
            if (withPinned) {
                append("pinned")
            }
            append(accountKey.toString())
            append(userKey.toString())
        }

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        val response =
            when (request) {
                PagingRequest.Refresh -> {
                    val pinned =
                        if (withPinned) {
                            service.userTimeline(
                                user_id = userKey.id,
                                limit = pageSize,
                                pinned = true,
                            )
                        } else {
                            emptyList()
                        }
                    service
                        .userTimeline(
                            user_id = userKey.id,
                            limit = pageSize,
                            only_media = onlyMedia,
                            exclude_replies = !withReplies,
                        ) + pinned
                }

                is PagingRequest.Prepend -> {
                    return PagingResult(
                        endOfPaginationReached = true,
                    )
                }

                is PagingRequest.Append -> {
                    service.userTimeline(
                        user_id = userKey.id,
                        limit = pageSize,
                        max_id = request.nextKey,
                        only_media = onlyMedia,
                        exclude_replies = !withReplies,
                    )
                }
            }

        return PagingResult(
            endOfPaginationReached = response.isEmpty(),
            data = response.render(accountKey),
            nextKey = response.next,
            previousKey = response.prev,
        )
    }
}
