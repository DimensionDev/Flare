package dev.dimension.flare.data.datasource.guest

import androidx.paging.PagingSource
import androidx.paging.PagingState
import dev.dimension.flare.data.network.mastodon.GuestMastodonService
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.model.mapper.renderGuest

internal class GuestSearchStatusPagingSource(
    private val service: GuestMastodonService,
    private val host: String,
    private val query: String,
) : PagingSource<String, UiTimeline>() {
    override fun getRefreshKey(state: PagingState<String, UiTimeline>): String? = null

    override suspend fun load(params: LoadParams<String>): LoadResult<String, UiTimeline> =
        try {
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

            LoadResult.Page(
                data = result?.map { it.renderGuest(host = host) }.orEmpty(),
                prevKey = null,
                nextKey = result?.lastOrNull()?.id,
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
}
