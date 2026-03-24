package dev.dimension.flare.data.datasource.nostr

import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.network.nostr.NostrService
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiTimelineV2

@OptIn(ExperimentalPagingApi::class)
internal class StatusDetailRemoteMediator(
    private val statusKey: MicroBlogKey,
    private val accountKey: MicroBlogKey,
    private val credentialProvider: suspend () -> UiAccount.Nostr.Credential,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String =
        buildString {
            append("status_detail_")
            append(statusKey.toString())
            append("_")
            append(accountKey.toString())
        }

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        val credential = credentialProvider()
        return when (request) {
            PagingRequest.Refresh ->
                PagingResult(
                    endOfPaginationReached = false,
                    data =
                        listOf(
                            NostrService.loadStatus(
                                credential = credential,
                                accountKey = accountKey,
                                statusKey = statusKey,
                            ),
                        ),
                    nextKey = pagingKey,
                )

            is PagingRequest.Append ->
                PagingResult(
                    endOfPaginationReached = true,
                    data =
                        NostrService.loadStatusContext(
                            credential = credential,
                            accountKey = accountKey,
                            statusKey = statusKey,
                            pageSize = pageSize,
                        ),
                )

            is PagingRequest.Prepend ->
                PagingResult(
                    endOfPaginationReached = true,
                )
        }
    }
}
