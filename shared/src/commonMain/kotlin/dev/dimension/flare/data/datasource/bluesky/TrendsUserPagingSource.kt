package dev.dimension.flare.data.datasource.bluesky

import androidx.paging.PagingState
import app.bsky.actor.GetSuggestionsQueryParams
import dev.dimension.flare.common.BasePagingSource
import dev.dimension.flare.data.network.bluesky.BlueskyService
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.mapper.render

internal class TrendsUserPagingSource(
    private val service: BlueskyService,
    private val accountKey: MicroBlogKey,
) : BasePagingSource<String, UiProfile>() {
    override fun getRefreshKey(state: PagingState<String, UiProfile>): String? = null

    override suspend fun doLoad(params: LoadParams<String>): LoadResult<String, UiProfile> {
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
