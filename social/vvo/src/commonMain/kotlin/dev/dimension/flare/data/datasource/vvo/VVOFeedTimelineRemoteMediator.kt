package dev.dimension.flare.data.datasource.vvo

import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.network.vvo.VVOService
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.mapper.render

internal sealed interface VVOFeed {
    data class Group(
        val id: String,
    ) : VVOFeed {
        init {
            require(id.isNotBlank()) { "Weibo group ID is required" }
        }
    }

    data object Circle : VVOFeed

    data object Private : VVOFeed
}

internal class VVOFeedTimelineRemoteMediator(
    private val service: VVOService,
    private val accountKey: MicroBlogKey,
    private val feed: VVOFeed,
) : CacheableRemoteLoader<UiTimelineV2> {
    // shortcut: Keep conservative cache boundaries until feed ordering is verified with a logged-in account.
    override val pagingKey: String =
        when (feed) {
            is VVOFeed.Group -> "vvo_group_${accountKey}_${feed.id}"
            VVOFeed.Circle -> "vvo_circle_$accountKey"
            VVOFeed.Private -> "vvo_private_$accountKey"
        }

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend) {
            return PagingResult(endOfPaginationReached = true)
        }
        service.ensureGroupLogin(accountKey)
        val cursor = (request as? PagingRequest.Append)?.nextKey
        val response =
            when (feed) {
                is VVOFeed.Group -> service.getGroupTimeline(groupId = feed.id, maxId = cursor)
                VVOFeed.Circle -> service.getCircleTimeline(maxId = cursor)
                VVOFeed.Private -> service.getPrivateTimeline(maxId = cursor)
            }.requireGroupData(accountKey)
        val statuses =
            checkNotNull(response.statuses ?: response.cards?.mapNotNull { it.mblog }) {
                "Invalid Weibo timeline"
            }
        val nextKey =
            response.maxId?.takeIf {
                statuses.isNotEmpty() && it.isNotBlank() && it != "0" && it != cursor
            }
        return PagingResult(
            data = statuses.map { it.render(accountKey) },
            nextKey = nextKey,
        )
    }
}
