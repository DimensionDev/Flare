package dev.dimension.flare.data.datasource.vvo

import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.network.vvo.VVOService
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.mapper.render

internal class GroupTimelineRemoteMediator(
    private val service: VVOService,
    private val accountKey: MicroBlogKey,
    private val groupId: String,
) : CacheableRemoteLoader<UiTimelineV2> {
    // shortcut: Keep conservative cache boundaries until group ordering is verified with a logged-in account.
    override val pagingKey: String = "vvo_group_${accountKey}_$groupId"

    init {
        require(groupId.isNotBlank()) { "Weibo group ID is required" }
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
        val response = service.getGroupTimeline(groupId = groupId, maxId = cursor).requireGroupData(accountKey)
        val statuses =
            checkNotNull(response.statuses ?: response.cards?.mapNotNull { it.mblog }) {
                "Invalid Weibo group timeline"
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
