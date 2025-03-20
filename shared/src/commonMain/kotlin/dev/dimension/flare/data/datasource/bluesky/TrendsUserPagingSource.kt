package dev.dimension.flare.data.datasource.bluesky

import androidx.paging.PagingState
import app.bsky.actor.GetSuggestionsQueryParams
import dev.dimension.flare.common.BasePagingSource
import dev.dimension.flare.data.network.bluesky.BlueskyService
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiUserV2
import dev.dimension.flare.ui.model.mapper.render

internal class TrendsUserPagingSource(
    private val service: BlueskyService,
    private val accountKey: MicroBlogKey,
) : BasePagingSource<String, UiUserV2>() {
    override fun getRefreshKey(state: PagingState<String, UiUserV2>): String? = null

    override suspend fun doLoad(params: LoadParams<String>): LoadResult<String, UiUserV2> {
        val response =
            service
                .getSuggestions(GetSuggestionsQueryParams(limit = params.loadSize.toLong(), cursor = params.key))
                .requireResponse()
        return LoadResult.Page(
            data = response.actors.map { it.render(accountKey) },
            prevKey = null,
            nextKey = response.cursor,
        )
    }
}
