package dev.dimension.flare.data.datasource.vvo

import androidx.paging.PagingSource
import androidx.paging.PagingState
import dev.dimension.flare.data.network.vvo.VVOService
import dev.dimension.flare.data.repository.LoginExpiredException
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.mapper.toUi

internal class CommentPagingSource(
    private val service: VVOService,
    private val accountKey: MicroBlogKey,
) : PagingSource<Int, UiStatus>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, UiStatus> {
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
                    it.toUi(accountKey)
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

    override fun getRefreshKey(state: PagingState<Int, UiStatus>): Int? = null
}
