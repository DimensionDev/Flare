package dev.dimension.flare.data.datasource.guest

import androidx.paging.PagingSource
import androidx.paging.PagingState
import dev.dimension.flare.data.network.mastodon.GuestMastodonService
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.mapper.toUi

internal class GuestStatusDetailPagingSource(
    private val statusKey: MicroBlogKey,
    private val statusOnly: Boolean,
) : PagingSource<Int, UiStatus>() {
    override fun getRefreshKey(state: PagingState<Int, UiStatus>): Int? = null

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, UiStatus> =
        try {
            val result =
                if (statusOnly) {
                    val current =
                        GuestMastodonService.lookupStatus(
                            statusKey.id,
                        )
                    listOf(current)
                } else {
                    val context =
                        GuestMastodonService.context(
                            statusKey.id,
                        )
                    val current =
                        GuestMastodonService.lookupStatus(
                            statusKey.id,
                        )
                    context.ancestors.orEmpty() + listOf(current) + context.descendants.orEmpty()
                }

            LoadResult.Page(
                data = result.map { it.toUi(GuestMastodonService.GuestKey) },
                prevKey = null,
                nextKey = null,
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
}
