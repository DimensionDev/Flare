package dev.dimension.flare.data.datasource.guest

import androidx.paging.PagingSource
import androidx.paging.PagingState
import dev.dimension.flare.data.network.mastodon.GuestMastodonService
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.mapper.toUi

internal class GuestUserTimelinePagingSource(
    private val userId: String,
    private val onlyMedia: Boolean = false,
) : PagingSource<String, UiStatus>() {
    override fun getRefreshKey(state: PagingState<String, UiStatus>): String? = null

    override suspend fun load(params: LoadParams<String>): LoadResult<String, UiStatus> =
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
                        it.toUi(GuestMastodonService.GuestKey)
                    },
                prevKey = null,
                nextKey = statuses.lastOrNull()?.id,
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
}
