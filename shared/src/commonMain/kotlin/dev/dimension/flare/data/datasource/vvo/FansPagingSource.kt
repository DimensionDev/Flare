package dev.dimension.flare.data.datasource.vvo

import androidx.paging.PagingState
import dev.dimension.flare.common.BasePagingSource
import dev.dimension.flare.data.network.vvo.VVOService
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiUserV2
import dev.dimension.flare.ui.model.mapper.render

internal class FansPagingSource(
    private val service: VVOService,
    private val accountKey: MicroBlogKey,
    private val userKey: MicroBlogKey,
) : BasePagingSource<Int, UiUserV2>() {
    override fun getRefreshKey(state: PagingState<Int, UiUserV2>): Int? = null

    private val containerId by lazy {
        if (accountKey == userKey) {
            "231016_-_selffans"
        } else {
            "231051_-_fans_-_${userKey.id}"
        }
    }

    override suspend fun doLoad(params: LoadParams<Int>): LoadResult<Int, UiUserV2> {
        val nextPage = params.key ?: 0
        val limit = params.loadSize
        val users =
            service
                .getContainerIndex(containerId = containerId, sinceId = nextPage.toString())
                .data
                ?.cards
                ?.filter { it.cardType == 11L && it.cardStyle == null }
                ?.mapNotNull {
                    it.cardGroup
                }?.flatMap { it }
                ?.mapNotNull { it.user }
                ?.map {
                    it.render(accountKey = accountKey)
                }.orEmpty()
        return LoadResult.Page(
            data = users,
            prevKey = null,
            nextKey = if (users.isEmpty()) null else users.size + nextPage,
        )
    }
}
