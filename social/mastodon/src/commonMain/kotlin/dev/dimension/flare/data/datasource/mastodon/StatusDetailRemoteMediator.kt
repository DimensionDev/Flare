package dev.dimension.flare.data.datasource.mastodon

import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.network.mastodon.MastodonService
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.mapper.render
import dev.dimension.flare.ui.model.mapper.renderStatusContext
import org.koin.core.component.KoinComponent

@OptIn(ExperimentalPagingApi::class)
internal class StatusDetailRemoteMediator(
    private val statusKey: MicroBlogKey,
    private val service: MastodonService,
    private val accountKey: MicroBlogKey,
    private val statusOnly: Boolean,
) : CacheableRemoteLoader<UiTimelineV2>,
    KoinComponent {
    override val collapseReplyChains: Boolean = false

    override val pagingKey: String =
        buildString {
            append("status_detail_")
            if (statusOnly) {
                append("status_only_")
            }
            append(statusKey.toString())
            append("_")
            append(accountKey.toString())
        }

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        val result =
            when (request) {
                is PagingRequest.Append -> {
                    if (statusOnly) {
                        return PagingResult(
                            endOfPaginationReached = true,
                        )
                    } else {
                        val context =
                            service.context(
                                statusKey.id,
                            )
                        val current =
                            service.lookupStatus(
                                statusKey.id,
                            )
                        renderStatusContext(
                            ancestors = context.ancestors.orEmpty(),
                            current = current,
                            descendants = context.descendants.orEmpty(),
                            accountKey = accountKey,
                        )
                    }
                }

                is PagingRequest.Prepend -> {
                    return PagingResult(
                        endOfPaginationReached = true,
                    )
                }

                PagingRequest.Refresh -> {
                    val current =
                        service.lookupStatus(
                            statusKey.id,
                        )
                    listOf(current).render(accountKey)
                }
            }
        val shouldLoadMore = !(request is PagingRequest.Append || statusOnly)

        return PagingResult(
            endOfPaginationReached = !shouldLoadMore,
            data = result,
            nextKey = if (shouldLoadMore) pagingKey else null,
        )
    }
}
