package dev.dimension.flare.data.datasource.mastodon

import androidx.paging.PagingState
import dev.dimension.flare.common.BasePagingSource
import dev.dimension.flare.data.network.mastodon.api.AccountResources
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.mapper.render

internal class MastodonFollowingPagingSource(
    private val service: AccountResources,
    private val accountKey: MicroBlogKey?,
    private val host: String,
    private val userKey: MicroBlogKey,
) : BasePagingSource<String, UiProfile>() {
    override fun getRefreshKey(state: PagingState<String, UiProfile>): String? = null

    override suspend fun doLoad(params: LoadParams<String>): LoadResult<String, UiProfile> {
        val maxId = params.key
        val limit = params.loadSize
        val response =
            service
                .following(
                    id = userKey.id,
                    limit = limit,
                    max_id = maxId,
                )
        return LoadResult.Page(
            data =
                response.map {
                    it.render(accountKey = accountKey, host = host)
                },
            prevKey = null,
            nextKey = response.next,
        )
    }
}
