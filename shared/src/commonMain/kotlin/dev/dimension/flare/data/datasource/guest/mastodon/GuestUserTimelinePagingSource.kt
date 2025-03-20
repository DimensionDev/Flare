package dev.dimension.flare.data.datasource.guest.mastodon

import androidx.paging.PagingState
import dev.dimension.flare.common.BasePagingSource
import dev.dimension.flare.data.network.mastodon.api.TimelineResources
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.model.mapper.renderGuest

internal class GuestUserTimelinePagingSource(
    private val service: TimelineResources,
    private val host: String,
    private val userId: String,
    private val withReply: Boolean = false,
    private val onlyMedia: Boolean = false,
) : BasePagingSource<String, UiTimeline>() {
    override fun getRefreshKey(state: PagingState<String, UiTimeline>): String? = null

    override suspend fun doLoad(params: LoadParams<String>): LoadResult<String, UiTimeline> {
        val maxId = params.key
        val limit = params.loadSize
        val statuses =
            service.userTimeline(
                user_id = userId,
                limit = limit,
                max_id = maxId,
                only_media = onlyMedia,
                exclude_replies = !withReply,
            )
        return LoadResult.Page(
            data =
                statuses.map {
                    it.renderGuest(host = host)
                },
            prevKey = null,
            nextKey = statuses.lastOrNull()?.id,
        )
    }
}
