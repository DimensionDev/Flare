package dev.dimension.flare.ui.presenter.article

import androidx.paging.testing.asSnapshot
import dev.dimension.flare.data.datasource.microblog.datasource.ArticleDataSource
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiArticle
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.UiTranslatableText
import dev.dimension.flare.ui.model.contentPostOrNull
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.render.toUiPlainText
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

class ArticlePresenterTest {
    private val accountKey = MicroBlogKey("me", "x.com")
    private val accountType = AccountType.Specific(accountKey)
    private val articleKey = MicroBlogKey("article", "x.com")

    @Test
    fun commentsFlow_keepsReplyOrderAcrossPages() =
        runTest {
            val firstReply = createPost(MicroBlogKey("reply-1", articleKey.host))
            val secondReply = createPost(MicroBlogKey("reply-2", articleKey.host))
            val requests = mutableListOf<PagingRequest>()
            val requestedKeys = mutableListOf<MicroBlogKey>()
            val dataSource =
                fakeArticleDataSource { requestedKey ->
                    requestedKeys += requestedKey
                    object : RemoteLoader<UiTimelineV2> {
                        override suspend fun load(
                            pageSize: Int,
                            request: PagingRequest,
                        ): PagingResult<UiTimelineV2> {
                            requests += request
                            return when (request) {
                                PagingRequest.Refresh -> {
                                    PagingResult(
                                        data = listOf(firstReply),
                                        nextKey = "next",
                                    )
                                }

                                is PagingRequest.Append -> {
                                    assertEquals("next", request.nextKey)
                                    PagingResult(
                                        data = listOf(secondReply),
                                        endOfPaginationReached = true,
                                    )
                                }

                                is PagingRequest.Prepend -> {
                                    PagingResult(endOfPaginationReached = true)
                                }
                            }
                        }
                    }
                }

            val snapshot =
                articleCommentsFlow(
                    dataSources = flowOf(dataSource),
                    articleKey = articleKey,
                ).asSnapshot {
                    appendScrollWhile { true }
                }

            assertEquals(listOf(articleKey), requestedKeys)
            assertEquals(
                listOf(firstReply.statusKey, secondReply.statusKey),
                snapshot.mapNotNull { it.contentPostOrNull()?.statusKey },
            )
            assertEquals(PagingRequest.Refresh, requests.first())
            assertIs<PagingRequest.Append>(requests.last())
        }

    @Test
    fun commentsFlow_isEmptyWhenLoaderReturnsNoComments() =
        runTest {
            val dataSource =
                fakeArticleDataSource {
                    object : RemoteLoader<UiTimelineV2> {
                        override suspend fun load(
                            pageSize: Int,
                            request: PagingRequest,
                        ): PagingResult<UiTimelineV2> =
                            PagingResult(
                                endOfPaginationReached = true,
                            )
                    }
                }

            val snapshot =
                articleCommentsFlow(
                    dataSources = flowOf(dataSource),
                    articleKey = articleKey,
                ).asSnapshot()

            assertTrue(snapshot.isEmpty())
        }

    private fun fakeArticleDataSource(comments: (MicroBlogKey) -> RemoteLoader<UiTimelineV2>): ArticleDataSource =
        object : ArticleDataSource {
            override suspend fun article(articleKey: MicroBlogKey): UiArticle = error("Article loading is not part of this test")

            override fun articleComments(articleKey: MicroBlogKey): RemoteLoader<UiTimelineV2> = comments(articleKey)
        }

    private fun createPost(statusKey: MicroBlogKey): UiTimelineV2.Post =
        UiTimelineV2.Post(
            platformType = PlatformType.xQt,
            images = persistentListOf(),
            sensitive = false,
            contentWarning = null,
            user = null,
            content = UiTranslatableText(statusKey.id.toUiPlainText()),
            actions = persistentListOf(),
            poll = null,
            statusKey = statusKey,
            card = null,
            createdAt = Instant.parse("2024-01-01T00:00:00Z").toUi(),
            emojiReactions = persistentListOf(),
            sourceChannel = null,
            visibility = null,
            replyToHandle = null,
            references = persistentListOf(),
            clickEvent = ClickEvent.Noop,
            accountType = accountType,
        )
}
