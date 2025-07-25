package dev.dimension.flare.data.datasource.vvo

import androidx.paging.PagingState
import dev.dimension.flare.common.BasePagingSource
import dev.dimension.flare.data.datasource.microblog.StatusEvent
import dev.dimension.flare.data.network.vvo.VVOService
import dev.dimension.flare.data.repository.LoginExpiredException
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.model.mapper.render

internal class LikePagingSource(
    private val service: VVOService,
    private val event: StatusEvent.VVO,
    private val accountKey: MicroBlogKey,
    private val onClearMarker: () -> Unit,
) : BasePagingSource<Int, UiTimeline>() {
    override suspend fun doLoad(params: LoadParams<Int>): LoadResult<Int, UiTimeline> {
        val config = service.config()
        if (config.data?.login != true) {
            return LoadResult.Error(
                LoginExpiredException(
                    accountKey = accountKey,
                    platformType = PlatformType.VVo,
                ),
            )
        }
        if (params.key == null) {
            onClearMarker.invoke()
        }
        val response =
            service.getAttitudes(
                page = params.key ?: 1,
            )

        val nextPage = params.key?.plus(1) ?: 2
        val data =
            response.data.orEmpty().filter { it.idStr != null }.map {
                it.render(accountKey, event)
            }

        return LoadResult.Page(
            data = data,
            prevKey = null,
            nextKey = nextPage.takeIf { it != params.key && data.any() },
        )
    }

    override fun getRefreshKey(state: PagingState<Int, UiTimeline>): Int? = null
}
