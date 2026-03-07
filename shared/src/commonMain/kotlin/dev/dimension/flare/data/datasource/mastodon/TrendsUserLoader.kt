package dev.dimension.flare.data.datasource.mastodon

import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.network.mastodon.api.TrendsResources
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.mapper.render

internal class TrendsUserLoader(
    private val service: TrendsResources,
    private val accountKey: MicroBlogKey?,
    private val host: String,
) : RemoteLoader<UiProfile> {
    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiProfile> {
        if (request !is PagingRequest.Refresh) {
            return PagingResult(
                data = emptyList(),
                nextKey = null,
                previousKey = null,
            )
        }
        val data =
            service
                .suggestionsUsers()
                .mapNotNull {
                    it.account?.render(accountKey = accountKey, host = host)
                }
        return PagingResult(
            data = data,
            nextKey = null,
            previousKey = null,
        )
    }
}
