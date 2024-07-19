package dev.dimension.flare.data.datasource.vvo

import androidx.paging.PagingSource
import androidx.paging.PagingState
import dev.dimension.flare.data.datasource.microblog.StatusEvent
import dev.dimension.flare.data.network.vvo.VVOService
import dev.dimension.flare.data.repository.LoginExpiredException
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.model.mapper.render

internal class CommentPagingSource(
    private val service: VVOService,
    private val event: StatusEvent.VVO,
    private val accountKey: MicroBlogKey,
) : PagingSource<Int, UiTimeline>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, UiTimeline> {
        return try {
            val config = service.config()
            if (config.data?.login != true) {
                return LoadResult.Error(
                    LoginExpiredException,
                )
            }
            val response =
                service.getComments(
                    page = params.key ?: 1,
                )

            val nextPage = params.key?.plus(1) ?: 2
            val data =
                response.data.orEmpty().map {
                    it.render(accountKey, event)
                }

            LoadResult.Page(
                data = data,
                prevKey = null,
                nextKey = nextPage.takeIf { it != params.key && data.any() },
            )
        } catch (e: Throwable) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, UiTimeline>): Int? = null
}
