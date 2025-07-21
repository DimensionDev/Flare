package dev.dimension.flare.data.datasource.vvo

import androidx.paging.PagingState
import dev.dimension.flare.common.BasePagingSource
import dev.dimension.flare.data.network.vvo.VVOService
import dev.dimension.flare.data.repository.LoginExpiredException
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.UiHashtag

internal class TrendHashtagPagingSource(
    private val accountKey: MicroBlogKey,
    private val service: VVOService,
) : BasePagingSource<Int, UiHashtag>() {
    private val containerId = "106003type=25&filter_type=realtimehot"

    override fun getRefreshKey(state: PagingState<Int, UiHashtag>): Int? = null

    override suspend fun doLoad(params: LoadParams<Int>): LoadResult<Int, UiHashtag> {
        val config = service.config()
        if (config.data?.login != true) {
            return LoadResult.Error(
                LoginExpiredException(
                    accountKey = accountKey,
                    platformType = PlatformType.VVo,
                ),
            )
        }
        service
            .getContainerIndex(containerId = containerId)
            .data
            ?.cards
            ?.flatMap {
                it.cardGroup.orEmpty()
            }?.mapNotNull {
                it.desc
            }?.map {
                UiHashtag(
                    hashtag = it,
                    description = null,
                    searchContent = "#$it#",
                )
            }?.toList()
            ?.take(10)
            .orEmpty()
            .let {
                return LoadResult.Page(
                    data = it,
                    prevKey = null,
                    nextKey = null,
                )
            }
    }
}
