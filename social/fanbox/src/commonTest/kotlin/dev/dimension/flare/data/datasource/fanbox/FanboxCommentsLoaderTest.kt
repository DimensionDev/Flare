package dev.dimension.flare.data.datasource.fanbox

import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.network.fanbox.FanboxCommentItem
import dev.dimension.flare.data.network.fanbox.FanboxCommentListResponse
import dev.dimension.flare.data.network.fanbox.FanboxService
import dev.dimension.flare.data.platform.FanboxCredential
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.contentPostOrNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FanboxCommentsLoaderTest {
    private val accountKey = MicroBlogKey(id = "account", host = "fanbox.cc")
    private val articleKey = MicroBlogKey(id = "article", host = "fanbox.cc")
    private val service =
        FanboxService(
            credentialFlow =
                flowOf(
                    FanboxCredential(
                        sessionId = "session",
                        userId = "user",
                    ),
                ),
        )

    @Test
    fun refreshKeepsRepliesAsQuotesInApiOrder() =
        runTest {
            val requests = mutableListOf<CommentRequest>()
            val loader =
                loader { postId, offset, limit ->
                    requests += CommentRequest(postId, offset, limit)
                    response(
                        items =
                            listOf(
                                comment(
                                    id = "12427633",
                                    replies =
                                        listOf(
                                            comment(
                                                id = "12430903",
                                                parentCommentId = "12430051",
                                                rootCommentId = "12427633",
                                            ),
                                            comment(
                                                id = "12430051",
                                                parentCommentId = "12427633",
                                                rootCommentId = "12427633",
                                            ),
                                        ),
                                ),
                                comment(id = "second"),
                            ),
                        nextUrl = "https://api.fanbox.cc/post.getComments?postId=article&offset=42&limit=7",
                    )
                }

            val result = loader.load(pageSize = 7, request = PagingRequest.Refresh)

            assertEquals(listOf(CommentRequest("article", 0, 7)), requests)
            assertEquals("42", result.nextKey)
            assertEquals(
                listOf("12427633", "second"),
                result.data.mapNotNull { item ->
                    item
                        .contentPostOrNull()
                        ?.statusKey
                        ?.id
                        ?.substringAfterLast(":comment:")
                },
            )
            val root = assertIs<UiTimelineV2.TimelinePostItem>(result.data[0])
            assertEquals(
                listOf("12430903", "12430051"),
                root.presentation.quotes.map { quote ->
                    quote.statusKey.id.substringAfterLast(":comment:")
                },
            )
            assertEquals(
                "12427633",
                root.post.statusKey.id
                    .substringAfterLast(":comment:"),
            )
            assertTrue(root.post.references.isEmpty())
        }

    @Test
    fun appendUsesNextOffsetAndEndsWithoutNextUrl() =
        runTest {
            val requests = mutableListOf<CommentRequest>()
            val loader =
                loader { postId, offset, limit ->
                    requests += CommentRequest(postId, offset, limit)
                    response(items = listOf(comment(id = "last")))
                }

            val result = loader.load(pageSize = 5, request = PagingRequest.Append(nextKey = "42"))

            assertEquals(listOf(CommentRequest("article", 42, 5)), requests)
            assertNull(result.nextKey)
            assertEquals("last", (result.data.single() as UiTimelineV2.Post).statusKey.id.substringAfterLast(":comment:"))
        }

    @Test
    fun prependEndsWithoutFetching() =
        runTest {
            var fetchCount = 0
            val loader =
                loader { _, _, _ ->
                    fetchCount += 1
                    response()
                }

            val result = loader.load(pageSize = 10, request = PagingRequest.Prepend(previousKey = "0"))

            assertEquals(0, fetchCount)
            assertTrue(result.data.isEmpty())
            assertNull(result.nextKey)
        }

    @Test
    fun emptyRefreshEndsPagination() =
        runTest {
            val loader = loader { _, _, _ -> response() }

            val result = loader.load(pageSize = 10, request = PagingRequest.Refresh)

            assertTrue(result.data.isEmpty())
            assertNull(result.nextKey)
        }

    private fun loader(
        fetchComments: suspend (postId: String, offset: Int, limit: Int) -> FanboxCommentListResponse,
    ): FanboxCommentsLoader =
        FanboxCommentsLoader(
            service = service,
            accountKey = accountKey,
            statusKey = articleKey,
            fetchComments = fetchComments,
        )

    private fun response(
        items: List<FanboxCommentItem> = emptyList(),
        nextUrl: String? = null,
    ): FanboxCommentListResponse =
        FanboxCommentListResponse(
            body =
                FanboxCommentListResponse.Body(
                    commentList =
                        FanboxCommentListResponse.CommentList(
                            items = items,
                            nextUrl = nextUrl,
                        ),
                ),
        )

    private fun comment(
        id: String,
        parentCommentId: String = "",
        rootCommentId: String = "",
        replies: List<FanboxCommentItem> = emptyList(),
    ): FanboxCommentItem =
        FanboxCommentItem(
            body = "Comment $id",
            createdDatetime = "2024-01-02T03:04:05+00:00",
            id = id,
            parentCommentId = parentCommentId,
            rootCommentId = rootCommentId,
            replies = replies,
        )

    private data class CommentRequest(
        val postId: String,
        val offset: Int,
        val limit: Int,
    )
}
