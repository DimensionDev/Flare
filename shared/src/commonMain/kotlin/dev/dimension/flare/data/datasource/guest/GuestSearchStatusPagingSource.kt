package dev.dimension.flare.data.datasource.guest

import androidx.paging.PagingSource
import androidx.paging.PagingState
import dev.dimension.flare.data.datasource.microblog.StatusEvent
import dev.dimension.flare.data.network.mastodon.GuestMastodonService
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.model.mapper.render

internal class GuestSearchStatusPagingSource(
    private val query: String,
    private val event: StatusEvent.Mastodon,
) : PagingSource<String, UiTimeline>() {
    override fun getRefreshKey(state: PagingState<String, UiTimeline>): String? = null

    override suspend fun load(params: LoadParams<String>): LoadResult<String, UiTimeline> =
        try {
            val result =
                if (query.startsWith("#")) {
                    GuestMastodonService.hashtagTimeline(
                        hashtag = query.removePrefix("#"),
                        limit = params.loadSize,
                        max_id = params.key,
                    )
                } else {
                    GuestMastodonService
                        .searchV2(
                            query = query,
                            limit = params.loadSize,
                            type = "statuses",
                            max_id = params.key,
                        ).statuses
                }

            LoadResult.Page(
                data = result?.map { it.render(GuestMastodonService.GuestKey, event) }.orEmpty(),
                prevKey = null,
                nextKey = result?.lastOrNull()?.id,
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
}
