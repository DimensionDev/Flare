package dev.dimension.flare.data.datasource.guest.mastodon

import androidx.paging.PagingState
import dev.dimension.flare.common.BaseTimelinePagingSource
import dev.dimension.flare.data.network.mastodon.GuestMastodonService
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.model.mapper.renderGuest

internal class GuestSearchStatusPagingSource(
    private val service: GuestMastodonService,
    private val host: String,
    private val query: String,
) : BaseTimelinePagingSource<String>() {
    override fun getRefreshKey(state: PagingState<String, UiTimeline>): String? = null

    override suspend fun doLoad(params: LoadParams<String>): LoadResult<String, UiTimeline> {
        val result =
            if (query.startsWith("#")) {
                service.hashtagTimeline(
                    hashtag = query.removePrefix("#"),
                    limit = params.loadSize,
                    max_id = params.key,
                )
            } else {
                service
                    .searchV2(
                        query = query,
                        limit = params.loadSize,
                        type = "statuses",
                        max_id = params.key,
                    ).statuses
            }

        return LoadResult.Page(
            data = result?.map { it.renderGuest(host = host) }.orEmpty(),
            prevKey = null,
            nextKey = result?.lastOrNull()?.id,
        )
    }
}
