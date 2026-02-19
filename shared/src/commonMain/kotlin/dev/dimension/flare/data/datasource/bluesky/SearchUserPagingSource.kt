package dev.dimension.flare.data.datasource.bluesky

import androidx.paging.PagingState
import app.bsky.actor.SearchActorsQueryParams
import dev.dimension.flare.common.BasePagingSource
import dev.dimension.flare.data.network.bluesky.BlueskyService
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.mapper.render

internal class SearchUserPagingSource(
    private val service: BlueskyService,
    private val accountKey: MicroBlogKey,
    private val query: String,
) : BasePagingSource<String, UiProfile>() {
    override fun getRefreshKey(state: PagingState<String, UiProfile>): String? = null

    override suspend fun doLoad(params: LoadParams<String>): LoadResult<String, UiProfile> {
        service
            .searchActors(
                SearchActorsQueryParams(q = query, limit = params.loadSize.toLong(), cursor = params.key),
            ).requireResponse()
            .let {
                return LoadResult.Page(
                    data = it.actors.map { it.render(accountKey) },
                    prevKey = null,
                    nextKey = it.cursor,
                )
            }
    }
}
