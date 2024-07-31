package dev.dimension.flare.data.datasource.xqt

import androidx.paging.PagingSource
import androidx.paging.PagingState
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.data.database.cache.mapper.cursor
import dev.dimension.flare.data.database.cache.mapper.users
import dev.dimension.flare.data.network.xqt.XQTService
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiUserV2
import dev.dimension.flare.ui.model.mapper.render

internal class SearchUserPagingSource(
    private val service: XQTService,
    private val accountKey: MicroBlogKey,
    private val query: String,
) : PagingSource<String, UiUserV2>() {
    override fun getRefreshKey(state: PagingState<String, UiUserV2>): String? = null

    override suspend fun load(params: LoadParams<String>): LoadResult<String, UiUserV2> {
        try {
            val response =
                service
                    .getSearchTimeline(
                        variables =
                            SearchRequest(
                                rawQuery = query,
                                count = params.loadSize.toLong(),
                                cursor = params.key,
                                product = "People",
                            ).encodeJson(),
                    ).body()
                    ?.data
                    ?.searchByRawQuery
                    ?.searchTimeline
                    ?.timeline
                    ?.instructions
                    .orEmpty()
            val cursor = response.cursor()
            val users = response.users()
            return LoadResult.Page(
                data = users.map { it.render(accountKey = accountKey) },
                prevKey = null,
                nextKey = cursor,
            )
        } catch (e: Throwable) {
            return LoadResult.Error(e)
        }
    }
}
