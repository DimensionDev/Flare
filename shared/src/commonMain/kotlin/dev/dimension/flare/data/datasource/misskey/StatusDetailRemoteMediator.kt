package dev.dimension.flare.data.datasource.misskey

import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.connect
import dev.dimension.flare.data.database.cache.model.DbPagingTimeline
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.network.misskey.MisskeyService
import dev.dimension.flare.data.network.misskey.api.model.IPinRequest
import dev.dimension.flare.data.network.misskey.api.model.NotesChildrenRequest
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.mapper.render
import kotlinx.coroutines.flow.firstOrNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.getValue

@OptIn(ExperimentalPagingApi::class)
internal class StatusDetailRemoteMediator(
    private val statusKey: MicroBlogKey,
    private val accountKey: MicroBlogKey,
    private val service: MisskeyService,
    private val statusOnly: Boolean,
) : CacheableRemoteLoader<UiTimelineV2>,
    KoinComponent {
    private val database: CacheDatabase by inject()
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
                    }
                    service
                        .notesChildren(
                            NotesChildrenRequest(
                                noteId = statusKey.id,
                                untilId = request.nextKey.takeIf { it.isNotEmpty() },
                                limit = pageSize,
                            ),
                        )
                }

                is PagingRequest.Prepend ->
                    return PagingResult(
                        endOfPaginationReached = true,
                    )

                PagingRequest.Refresh -> {
                    if (!database.pagingTimelineDao().existsPaging(accountKey, pagingKey)) {
                        val status =
                            database
                                .statusDao()
                                .get(statusKey, AccountType.Specific(accountKey))
                                .firstOrNull()
                        status?.let {
                            database.connect {
                                database
                                    .pagingTimelineDao()
                                    .insertAll(
                                        listOf(
                                            DbPagingTimeline(
                                                statusKey = statusKey,
                                                pagingKey = pagingKey,
                                                sortId = 0,
                                            ),
                                        ),
                                    )
                            }
                        }
                    }
                    val current =
                        service
                            .notesShow(
                                IPinRequest(noteId = statusKey.id),
                            )
                    if (statusOnly) {
                        listOf(current)
                    } else {
                        listOfNotNull(current.reply, current)
                    }
                }
            }

        return PagingResult(
            endOfPaginationReached = statusOnly || result.isEmpty(),
            data =
                result.render(accountKey),
            nextKey =
                if (request == PagingRequest.Refresh) {
                    ""
                } else {
                    result.lastOrNull()?.id
                },
        )
    }
}
