package dev.dimension.flare.ui.presenter.status

import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.ShareRepliesLoader
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.TranslationDisplayState
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.UiTranslatableText
import dev.dimension.flare.ui.model.contentPostOrNull
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.render.toUiPlainText
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class StatusShareTest {
    @Test
    fun contextUnpacksNestedRepliesWithoutAncestorsQuotesOrDuplicates() =
        runTest {
            val parent = post("parent")
            val root = post("root")
            val first = post("first")
            val second = post("second")
            val quote = post("quote")
            val chain =
                UiTimelineV2.TimelinePostItem(
                    second,
                    UiTimelineV2.PostPresentation(
                        inlineParents =
                            persistentListOf(
                                UiTimelineV2.TimelinePostItem(parent),
                                UiTimelineV2.TimelinePostItem(root),
                                UiTimelineV2.TimelinePostItem(first),
                            ),
                        quotes = persistentListOf(quote),
                    ),
                )
            val loader = ShareRepliesLoader(loader { PagingResult(data = listOf(parent, root, chain, first)) }, root.statusKey)
            val result = loader.load(20, PagingRequest.Refresh)
            assertEquals(listOf(first.statusKey, second.statusKey), result.data.map { it.statusKey })
            val item = result.data.last() as UiTimelineV2.TimelinePostItem
            assertTrue(item.presentation.inlineParents.isEmpty())
            assertEquals(listOf(quote), item.presentation.quotes)
        }

    @Test
    fun sharingARepostDoesNotOfferItsDisplayedPostAsAReply() =
        runTest {
            val wrapper = post("repost")
            val original = post("original")
            val item = UiTimelineV2.TimelinePostItem(wrapper, UiTimelineV2.PostPresentation(repost = original))
            val loader = ShareRepliesLoader(loader { PagingResult(data = listOf(item, original, post("reply"))) }, wrapper.statusKey)
            assertEquals(listOf("reply"), loader.load(20, PagingRequest.Refresh).data.map { it.statusKey.id })
        }

    @Test
    fun commentChildrenAreIndividuallySelectableInsteadOfImplicitlyQuoted() =
        runTest {
            val parent = post("comment")
            val child = post("child")
            val item = UiTimelineV2.TimelinePostItem(parent, UiTimelineV2.PostPresentation(quotes = persistentListOf(child)))
            val loader = ShareRepliesLoader(loader { PagingResult(data = listOf(item)) }, post("root").statusKey, comments = true)
            val result = loader.load(20, PagingRequest.Refresh)
            assertEquals(listOf(parent.statusKey, child.statusKey), result.data.map { it.statusKey })
            assertTrue((result.data.first() as UiTimelineV2.TimelinePostItem).presentation.quotes.isEmpty())
            assertEquals(
                parent.statusKey,
                result.data
                    .last()
                    .contentPostOrNull()!!
                    .references
                    .single()
                    .statusKey,
            )
        }

    @Test
    fun selectionUsesDetailOrderAndCancelRestoresConfirmedSnapshotAcrossPagination() =
        runTest {
            val first = post("first")
            val second = post("second")
            val third = post("third")
            val session =
                StatusShareSession {
                    loader { request ->
                        if (request == PagingRequest.Refresh) {
                            PagingResult(data = listOf(first, second), nextKey = "next")
                        } else {
                            PagingResult(data = listOf(second, third))
                        }
                    }
                }
            session.beginSelection()
            session.loadMore()
            session.toggle(second.statusKey)
            session.toggle(first.statusKey)
            session.loadMore()
            session.confirm()
            assertEquals(listOf<UiTimelineV2>(first, second), session.selectedReplies)
            assertEquals(listOf<UiTimelineV2>(first, second, third), session.replies)
            session.beginSelection()
            session.clear()
            session.toggle(third.statusKey)
            session.cancel()
            assertEquals(listOf<UiTimelineV2>(first, second), session.selectedReplies)
            session.beginSelection()
            session.clear()
            session.confirm()
            assertTrue(session.selectedReplies.isEmpty())
        }

    @Test
    fun failedContinuationKeepsSelectionAndRetriesTheSameCursor() =
        runTest {
            val first = post("first")
            var fail = true
            val requests = mutableListOf<PagingRequest>()
            val session =
                StatusShareSession {
                    loader { request ->
                        requests += request
                        if (request == PagingRequest.Refresh) {
                            PagingResult(data = listOf(first), nextKey = "cursor")
                        } else if (fail) {
                            error("offline")
                        } else {
                            PagingResult(data = listOf(post("second")))
                        }
                    }
                }
            session.beginSelection()
            session.loadMore()
            session.toggle(first.statusKey)
            session.loadMore()
            assertNotNull(session.error)
            assertEquals(setOf(first.statusKey), session.draftKeys)
            fail = false
            session.loadMore()
            assertNull(session.error)
            assertEquals(listOf(PagingRequest.Refresh, PagingRequest.Append("cursor"), PagingRequest.Append("cursor")), requests)
        }

    @Test
    fun openingFetchesInitialContextButDoesNotDrainFurtherPages() =
        runTest {
            val requests = mutableListOf<PagingRequest>()
            val session =
                StatusShareSession {
                    loader { request ->
                        requests += request
                        if (request == PagingRequest.Refresh) {
                            PagingResult(nextKey = "")
                        } else {
                            PagingResult(data = listOf(post("reply")), nextKey = "more")
                        }
                    }
                }
            session.loadMore()
            assertEquals(listOf(PagingRequest.Refresh, PagingRequest.Append("")), requests)
            assertTrue(session.hasMore)
        }

    @Test
    fun nestedCommentPagesStayUnderTheirParentAndRetryWithoutLosingSelection() =
        runTest {
            val parent = post("parent")
            val child = post("child")
            val nextThread = post("next-thread")
            val nestedRequests = mutableListOf<PagingRequest>()
            var fail = true
            val session =
                StatusShareSession(childLoader = { reply ->
                    if (reply.statusKey != parent.statusKey) {
                        null
                    } else {
                        loader { request ->
                            nestedRequests += request
                            if (request == PagingRequest.Refresh) {
                                PagingResult(data = listOf(child), nextKey = "children")
                            } else if (fail) {
                                error("offline")
                            } else {
                                PagingResult(data = listOf(child, post("second-child")))
                            }
                        }
                    }
                }) { loader { PagingResult(data = listOf(parent, child, nextThread)) } }
            session.beginSelection()
            session.loadMore()
            session.toggle(child.statusKey)
            session.loadChildren(parent.statusKey)
            session.loadChildren(parent.statusKey)
            assertNotNull(session.error)
            assertEquals(setOf(child.statusKey), session.draftKeys)
            fail = false
            session.loadMore()
            assertEquals(listOf("parent", "child", "second-child", "next-thread"), session.replies.map { it.statusKey.id })
            assertEquals(listOf(PagingRequest.Refresh, PagingRequest.Append("children"), PagingRequest.Append("children")), nestedRequests)
            assertTrue(session.expandableReplies.isEmpty())
            session.confirm()
            assertEquals(listOf(child.statusKey), session.selectedReplies.map { it.statusKey })
        }

    @Test
    fun screenshotAlwaysUsesOriginalTextIncludingQuotedPosts() {
        val translated = post("translated").copy(translationDisplayState = TranslationDisplayState.Translated)
        val result =
            UiTimelineV2.TimelinePostItem(translated, UiTimelineV2.PostPresentation(quotes = persistentListOf(translated))).forScreenshot()
                as UiTimelineV2.TimelinePostItem
        assertEquals(TranslationDisplayState.Hidden, result.post.translationDisplayState)
        assertEquals(
            TranslationDisplayState.Hidden,
            result.presentation.quotes
                .single()
                .translationDisplayState,
        )
    }

    @Test
    fun imageBudgetRejectsOverflowEmptyAndOversizedImages() {
        assertTrue(StatusShareImageLimits.canRender(1080, 10_000))
        assertFalse(StatusShareImageLimits.canRender(1080, 12_000))
        assertFalse(StatusShareImageLimits.canRender(360, 16_385))
        assertFalse(StatusShareImageLimits.canRender(Int.MAX_VALUE, Int.MAX_VALUE))
        assertFalse(StatusShareImageLimits.canRender(0, 100))
    }

    private fun loader(load: suspend (PagingRequest) -> PagingResult<UiTimelineV2>): RemoteLoader<UiTimelineV2> =
        object : RemoteLoader<UiTimelineV2> {
            override suspend fun load(
                pageSize: Int,
                request: PagingRequest,
            ): PagingResult<UiTimelineV2> = load(request)
        }

    private fun post(id: String): UiTimelineV2.Post =
        UiTimelineV2.Post(
            platformId = "Mastodon",
            images = persistentListOf(),
            sensitive = false,
            contentWarning = null,
            user = null,
            content = UiTranslatableText(id.toUiPlainText()),
            actions = persistentListOf(),
            poll = null,
            statusKey = MicroBlogKey(id, "example.org"),
            card = null,
            createdAt = Instant.parse("2026-01-01T00:00:00Z").toUi(),
            clickEvent = ClickEvent.Noop,
            accountType = AccountType.Specific(MicroBlogKey("me", "example.org")),
        )
}
