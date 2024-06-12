package dev.dimension.flare.data.datasource.guest

import androidx.paging.PagingSource
import androidx.paging.PagingState
import dev.dimension.flare.data.network.mastodon.GuestMastodonService
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.mapper.toUi

internal class GuestSearchStatusPagingSource(
    private val query: String,
) : PagingSource<String, UiStatus>() {
    override fun getRefreshKey(state: PagingState<String, UiStatus>): String? = null

    override suspend fun load(params: LoadParams<String>): LoadResult<String, UiStatus> =
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
                data = result?.map { it.toUi(GuestMastodonService.GuestKey) }.orEmpty(),
                prevKey = null,
                nextKey = result?.lastOrNull()?.id,
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
}
