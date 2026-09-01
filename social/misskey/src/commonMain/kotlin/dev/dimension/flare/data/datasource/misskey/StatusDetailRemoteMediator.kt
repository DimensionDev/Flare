package dev.dimension.flare.data.datasource.misskey

import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.network.misskey.MisskeyService
import dev.dimension.flare.data.network.misskey.api.model.IPinRequest
import dev.dimension.flare.data.network.misskey.api.model.Note
import dev.dimension.flare.data.network.misskey.api.model.NotesChildrenRequest
import dev.dimension.flare.data.network.misskey.api.model.NotesConversationRequest
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.mapper.render

@OptIn(ExperimentalPagingApi::class)
internal class StatusDetailRemoteMediator(
    private val statusKey: MicroBlogKey,
    private val accountKey: MicroBlogKey,
    private val service: MisskeyService,
    private val statusOnly: Boolean,
) : CacheableRemoteLoader<UiTimelineV2> {
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
        val (result, nextKey) =
            when (request) {
                is PagingRequest.Append -> {
                    if (statusOnly) {
                        return PagingResult(
                            endOfPaginationReached = true,
                        )
                    }
                    loadReplyPage(
                        pageSize = pageSize,
                        untilId = request.nextKey.takeIf { it.isNotEmpty() },
                    )
                }

                is PagingRequest.Prepend -> {
                    return PagingResult(
                        endOfPaginationReached = true,
                    )
                }

                PagingRequest.Refresh -> {
                    val current =
                        service
                            .notesShow(
                                IPinRequest(noteId = statusKey.id),
                            )
                    if (statusOnly) {
                        listOf(current) to null
                    } else {
                        val ancestors =
                            service
                                .notesConversation(
                                    NotesConversationRequest(
                                        noteId = statusKey.id,
                                        limit = MAX_CONTEXT_NOTES,
                                    ),
                                ).asReversed()
                                .ifEmpty { listOfNotNull(current.reply) }
                        (ancestors + current).distinctBy { it.id } to ""
                    }
                }
            }

        return PagingResult(
            endOfPaginationReached = statusOnly || result.isEmpty(),
            data =
                result.render(accountKey),
            nextKey = nextKey,
        )
    }

    private suspend fun loadReplyPage(
        pageSize: Int,
        untilId: String?,
    ): Pair<List<Note>, String?> {
        val limit = pageSize.coerceIn(1, MAX_CONTEXT_NOTES)
        val directChildren =
            service.notesChildren(
                NotesChildrenRequest(
                    noteId = statusKey.id,
                    untilId = untilId,
                    limit = limit,
                ),
            )
        val result = mutableListOf<Note>()
        val visited = mutableSetOf(statusKey.id)
        var nestedRequests = 0

        suspend fun appendChildren(
            parentId: String,
            children: List<Note>,
        ) {
            for (child in children) {
                if (result.size >= MAX_CONTEXT_NOTES) return
                if (!visited.add(child.id)) continue
                result += child
                if (
                    result.size >= MAX_CONTEXT_NOTES ||
                    child.replyId != parentId ||
                    child.repliesCount <= 0 ||
                    nestedRequests >= MAX_NESTED_CONTEXT_REQUESTS
                ) {
                    continue
                }
                nestedRequests++
                appendChildren(
                    parentId = child.id,
                    children =
                        service.notesChildren(
                            NotesChildrenRequest(
                                noteId = child.id,
                                limit = minOf(limit, MAX_CONTEXT_NOTES - result.size),
                            ),
                        ),
                )
            }
        }

        // ponytail: Bound recursive API work; raise the budgets only if real threads regularly exceed them.
        appendChildren(statusKey.id, directChildren)
        return result to directChildren.lastOrNull()?.id
    }

    private companion object {
        const val MAX_CONTEXT_NOTES = 100
        const val MAX_NESTED_CONTEXT_REQUESTS = 20
    }
}
