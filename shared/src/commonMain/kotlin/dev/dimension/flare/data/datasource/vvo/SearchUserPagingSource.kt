package dev.dimension.flare.data.datasource.vvo

import androidx.paging.PagingState
import dev.dimension.flare.common.BasePagingSource
import dev.dimension.flare.data.network.vvo.VVOService
import dev.dimension.flare.data.repository.LoginExpiredException
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.UiUserV2
import dev.dimension.flare.ui.model.mapper.render

internal class SearchUserPagingSource(
    private val service: VVOService,
    private val accountKey: MicroBlogKey,
    private val query: String,
) : BasePagingSource<Int, UiUserV2>() {
    private val containerId by lazy {
        "100103type=3&q=$query&t="
    }

    override fun getRefreshKey(state: PagingState<Int, UiUserV2>): Int? = null

    override suspend fun doLoad(params: LoadParams<Int>): LoadResult<Int, UiUserV2> {
        val config = service.config()
        if (config.data?.login != true) {
            return LoadResult.Error(
                LoginExpiredException(
                    accountKey = accountKey,
                    platformType = PlatformType.VVo,
                ),
            )
        }
        val response =
            service.getContainerIndex(
                containerId = containerId,
                pageType = "searchall",
                page = params.key,
            )
        val users =
            response.data
                ?.cards
                ?.flatMap {
                    it.cardGroup.orEmpty()
                }?.mapNotNull {
                    it.user
                }.orEmpty()
        return LoadResult.Page(
            data = users.map { it.render(accountKey = accountKey) },
            prevKey = null,
            nextKey = if (users.isEmpty()) null else params.key?.plus(1),
        )
    }
}
