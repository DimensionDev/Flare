package dev.dimension.flare.data.datasource.bluesky

import androidx.paging.PagingSource
import androidx.paging.PagingState
import app.bsky.actor.SearchActorsQueryParams
import dev.dimension.flare.data.network.bluesky.BlueskyService
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiUserV2
import dev.dimension.flare.ui.model.mapper.render

internal class SearchUserPagingSource(
    private val service: BlueskyService,
    private val accountKey: MicroBlogKey,
    private val query: String,
) : PagingSource<String, UiUserV2>() {
    override fun getRefreshKey(state: PagingState<String, UiUserV2>): String? = null

    override suspend fun load(params: LoadParams<String>): LoadResult<String, UiUserV2> {
        try {
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
        } catch (e: Throwable) {
            return LoadResult.Error(e)
        }
    }
}
