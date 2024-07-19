package dev.dimension.flare.data.datasource.guest

import androidx.paging.PagingSource
import androidx.paging.PagingState
import dev.dimension.flare.data.datasource.microblog.StatusEvent
import dev.dimension.flare.data.network.mastodon.GuestMastodonService
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.mapper.render
import dev.dimension.flare.ui.render.Render

internal class GuestStatusDetailPagingSource(
    private val statusKey: MicroBlogKey,
    private val event: StatusEvent.Mastodon,
    private val statusOnly: Boolean,
) : PagingSource<Int, Render.Item>() {
    override fun getRefreshKey(state: PagingState<Int, Render.Item>): Int? = null

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Render.Item> =
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
                data = result.map { it.render(GuestMastodonService.GuestKey, event) },
                prevKey = null,
                nextKey = null,
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
}
