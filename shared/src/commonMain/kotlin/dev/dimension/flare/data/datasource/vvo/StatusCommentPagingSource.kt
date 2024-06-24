package dev.dimension.flare.data.datasource.vvo

import androidx.paging.PagingSource
import androidx.paging.PagingState
import dev.dimension.flare.data.network.vvo.VVOService
import dev.dimension.flare.data.repository.LoginExpiredException
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.mapper.toUi

internal class StatusCommentPagingSource(
    private val service: VVOService,
    private val statusKey: MicroBlogKey,
    private val accountKey: MicroBlogKey,
) : PagingSource<Long, UiStatus.VVOComment>() {
    override suspend fun load(params: LoadParams<Long>): LoadResult<Long, UiStatus.VVOComment> {
        return try {
            val config = service.config()
            if (config.data?.login != true) {
                return LoadResult.Error(
                    LoginExpiredException,
                )
            }
            val response =
                service.getHotComments(
                    id = statusKey.id,
                    mid = statusKey.id,
                    maxId = params.key,
                )

            val nextPage = response.data?.maxID?.takeIf { it != 0L }
            val data =
                response.data?.data.orEmpty().map {
                    it.toUi(accountKey)
                }

            LoadResult.Page(
                data = data,
                prevKey = null,
                nextKey = nextPage.takeIf { it != params.key },
            )
        } catch (e: Throwable) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Long, UiStatus.VVOComment>): Long? = null
}
