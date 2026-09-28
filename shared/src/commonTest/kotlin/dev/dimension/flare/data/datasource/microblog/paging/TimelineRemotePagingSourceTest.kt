package dev.dimension.flare.data.datasource.microblog.paging

import androidx.paging.PagingSource
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.UiTranslatableText
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.render.toUiPlainText
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class TimelineRemotePagingSourceTest {
    @Test
    fun equalHashesKeepDistinctItemsAndDuplicateItemsAreCollapsed() =
        runTest {
            val first = feed("Aa")
            val second = feed("BB")
            assertEquals(first.hashCode(), second.hashCode())
            val explicit = feed("explicit").copy(itemKey = "existing-key")
            val source = loader { PagingResult(listOf(first, second, first, explicit)) }.toTimelinePagingSource()

            val items = source.refresh().data

            assertEquals(listOf(first.url, second.url, explicit.url), items.map { (it as UiTimelineV2.Feed).url })
            assertEquals(3, items.map { assertNotNull(it.itemKey) }.distinct().size)
            assertEquals("existing-key", items.last().itemKey)
        }

    @Test
    fun overlappingPagesKeepCursorsAndRefreshKeepsItemIdentity() =
        runTest {
            var refreshed = false
            var failAppend = true
            val remote =
                loader { request ->
                    when (request) {
                        PagingRequest.Refresh -> {
                            PagingResult(
                                data =
                                    if (refreshed) {
                                        listOf(
                                            feed("new"),
                                            feed("a", "updated"),
                                            feed("b"),
                                        )
                                    } else {
                                        listOf(feed("a"), feed("b"))
                                    },
                                nextKey = "overlap",
                            )
                        }

                        is PagingRequest.Append -> {
                            if (failAppend) {
                                failAppend = false
                                error("temporary failure")
                            }
                            if (request.nextKey == "overlap") {
                                PagingResult(listOf(feed("b")), nextKey = "more")
                            } else {
                                PagingResult(listOf(feed("b"), feed("c")))
                            }
                        }

                        is PagingRequest.Prepend -> {
                            PagingResult(listOf(feed("a"), feed("earlier")))
                        }
                    }
                }
            val source = remote.toTimelinePagingSource()
            val first = source.refresh()
            assertEquals("overlap", first.nextKey)
            assertIs<PagingSource.LoadResult.Error<String, UiTimelineV2>>(source.load(append("overlap")))
            val overlap = assertIs<PagingSource.LoadResult.Page<String, UiTimelineV2>>(source.load(append("overlap")))
            assertTrue(overlap.data.isEmpty())
            assertEquals("more", overlap.nextKey)
            val next = assertIs<PagingSource.LoadResult.Page<String, UiTimelineV2>>(source.load(append("more")))
            assertEquals(listOf(feed("c").statusKey), next.data.map { it.statusKey })
            val earlier =
                assertIs<PagingSource.LoadResult.Page<String, UiTimelineV2>>(
                    source.load(PagingSource.LoadParams.Prepend("earlier", 20, false)),
                )
            assertEquals(listOf(feed("earlier").statusKey), earlier.data.map { it.statusKey })

            refreshed = true
            val refreshedItems = source.refresh().data
            assertEquals(3, refreshedItems.size)
            assertEquals(first.data.map { it.itemKey }, refreshedItems.drop(1).map { it.itemKey })
            assertEquals(refreshedItems, remote.toTimelinePagingSource().refresh().data)
        }

    @Test
    fun notificationsAndAccountsKeepIndependentIdentities() =
        runTest {
            val post =
                UiTimelineV2.Post(
                    platformId = "test",
                    images = persistentListOf(),
                    sensitive = false,
                    contentWarning = null,
                    user = null,
                    content = UiTranslatableText("post".toUiPlainText()),
                    actions = persistentListOf(),
                    poll = null,
                    statusKey = MicroBlogKey("post", "example.com"),
                    card = null,
                    createdAt = Instant.fromEpochSeconds(1).toUi(),
                    clickEvent = ClickEvent.Noop,
                    accountType = AccountType.Guest,
                )
            val first =
                UiTimelineV2.TimelinePostItem(
                    post,
                    UiTimelineV2.PostPresentation(notificationKey = MicroBlogKey("like", "example.com")),
                )
            val second = first.copy(presentation = first.presentation.copy(notificationKey = MicroBlogKey("reply", "example.com")))
            val otherAccount = first.copy(post = post.copy(accountType = AccountType.Specific(MicroBlogKey("me", "example.com"))))
            val hostlessPost = post.copy(statusKey = MicroBlogKey("post@example.com", ""))
            assertEquals(post.statusKey.toString(), hostlessPost.statusKey.toString())
            val source = loader { PagingResult(listOf(first, second, first, otherAccount, post, hostlessPost)) }.toTimelinePagingSource()

            val items = source.refresh().data

            assertEquals(5, items.size)
            assertEquals(5, items.map { assertNotNull(it.itemKey) }.distinct().size)
        }

    private fun loader(load: suspend (PagingRequest) -> PagingResult<UiTimelineV2>): RemoteLoader<UiTimelineV2> =
        object : RemoteLoader<UiTimelineV2> {
            override suspend fun load(
                pageSize: Int,
                request: PagingRequest,
            ): PagingResult<UiTimelineV2> = load(request)
        }

    private suspend fun PagingSource<String, UiTimelineV2>.refresh(): PagingSource.LoadResult.Page<String, UiTimelineV2> =
        assertIs(load(PagingSource.LoadParams.Refresh(null, 20, false)))

    private fun append(key: String) = PagingSource.LoadParams.Append(key, 20, false)

    private fun feed(
        id: String,
        title: String = "title",
    ) = UiTimelineV2.Feed(
        title = title,
        description = null,
        url = "https://example.com/$id",
        createdAt = Instant.fromEpochSeconds(1).toUi(),
        source = UiTimelineV2.Feed.Source("test", null),
        clickEvent = ClickEvent.Noop,
        accountType = AccountType.Guest,
    )
}
