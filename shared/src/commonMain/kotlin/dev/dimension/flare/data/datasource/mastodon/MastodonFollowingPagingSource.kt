package dev.dimension.flare.data.datasource.mastodon

import androidx.paging.PagingSource
import androidx.paging.PagingState
import dev.dimension.flare.data.network.mastodon.api.AccountResources
import dev.dimension.flare.data.network.mastodon.api.model.MastodonPaging
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiUserV2
import dev.dimension.flare.ui.model.mapper.render

internal class MastodonFollowingPagingSource(
    private val service: AccountResources,
    private val accountKey: MicroBlogKey?,
    private val host: String,
    private val userKey: MicroBlogKey,
) : PagingSource<String, UiUserV2>() {
    override fun getRefreshKey(state: PagingState<String, UiUserV2>): String? = null

    override suspend fun load(params: LoadParams<String>): LoadResult<String, UiUserV2> {
        try {
            val maxId = params.key
            val limit = params.loadSize
            val response =
                service
                    .following(
                        id = userKey.id,
                        limit = limit,
                        max_id = maxId,
                    ).let { MastodonPaging.from(it) }
            return LoadResult.Page(
                data =
                    response.map {
                        it.render(accountKey = accountKey, host = host)
                    },
                prevKey = null,
                nextKey = response.next,
            )
        } catch (e: Throwable) {
            return LoadResult.Error(e)
        }
    }
}
