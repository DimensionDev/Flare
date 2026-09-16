package dev.dimension.flare.data.datasource.nostr

import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.data.datasource.microblog.paging.ContextPageUpdate
import dev.dimension.flare.data.datasource.microblog.paging.ContextUpdate
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.datasource.microblog.paging.PostContextLoader
import dev.dimension.flare.data.datasource.microblog.paging.toContextUpdate
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2

@OptIn(ExperimentalPagingApi::class)
internal class StatusDetailRemoteMediator(
    override val statusKey: MicroBlogKey,
    override val accountKey: MicroBlogKey,
    private val serviceManager: NostrServiceManager,
) : PostContextLoader {
    override val pagingKey: String =
        buildString {
            append("status_detail_")
            append(statusKey.toString())
            append("_")
            append(accountKey.toString())
        }

    override fun contextUpdate(
        request: PagingRequest,
        result: PagingResult<UiTimelineV2>,
        initial: Boolean,
    ): ContextUpdate =
        if (request == PagingRequest.Refresh) {
            ContextUpdate(posts = result.data)
        } else {
            // The context is rendered as reply chains, which can include the focal post inline.
            result.toContextUpdate(statusKey, request, initial).copy(
                before = if (initial) ContextPageUpdate(emptyList(), cursor = null, replace = true) else null,
            )
        }

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> =
        when (request) {
            PagingRequest.Refresh -> {
                PagingResult(
                    endOfPaginationReached = false,
                    data =
                        listOf(
                            serviceManager.withService {
                                it.loadStatus(
                                    statusKey = statusKey,
                                )
                            },
                        ),
                    nextKey = pagingKey,
                )
            }

            is PagingRequest.Append -> {
                PagingResult(
                    endOfPaginationReached = true,
                    data =
                        serviceManager.withService {
                            it.loadStatusContext(
                                statusKey = statusKey,
                                pageSize = pageSize,
                            )
                        },
                )
            }

            is PagingRequest.Prepend -> {
                PagingResult(
                    endOfPaginationReached = true,
                )
            }
        }
}
