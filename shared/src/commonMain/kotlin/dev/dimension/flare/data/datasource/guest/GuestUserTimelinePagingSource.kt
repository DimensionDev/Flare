package dev.dimension.flare.data.datasource.guest

import androidx.paging.PagingSource
import androidx.paging.PagingState
import dev.dimension.flare.data.network.mastodon.GuestMastodonService
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.model.mapper.render

internal class GuestUserTimelinePagingSource(
    private val host: String,
    private val userId: String,
    private val onlyMedia: Boolean = false,
) : PagingSource<String, UiTimeline>() {
    override fun getRefreshKey(state: PagingState<String, UiTimeline>): String? = null

    override suspend fun load(params: LoadParams<String>): LoadResult<String, UiTimeline> =
        try {
            val maxId = params.key
            val limit = params.loadSize
            val statuses =
                GuestMastodonService.userTimeline(
                    user_id = userId,
                    limit = limit,
                    max_id = maxId,
                    only_media = onlyMedia,
                )
            LoadResult.Page(
                data =
                    statuses.map {
                        it.render(host = host, accountKey = null, event = null)
                    },
                prevKey = null,
                nextKey = statuses.lastOrNull()?.id,
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
}
