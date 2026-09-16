package dev.dimension.flare.common

import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.compose.collectAsLazyPagingItems
import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import dev.dimension.flare.RobolectricTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PagingStatePublicationTest : RobolectricTest() {
    @Test
    fun publishedItemKeysSurviveAShorterPagingGeneration() =
        withPagingStates { pages, states ->
            pages.value = page(*Array(26) { "post-$it" })
            runCurrent()
            val published = states.last()
            val itemKey = published.itemKey { it }

            pages.value = page(*Array(25) { "post-$it" })
            runCurrent()

            assertEquals(26, published.itemCount)
            assertEquals(25, states.last().itemCount)
            val currentItemKey = states.last().itemKey { it }
            assertEquals(List(25) { "post-$it" }, List(25, currentItemKey))
            assertEquals("post-25", itemKey(25))
        }

    @Test
    fun publishedContentTypesSurviveAShorterPagingGeneration() =
        withPagingStates { pages, states ->
            pages.value = page("main", "reply")
            runCurrent()
            val contentType = states.last().itemContentType { it }

            pages.value = page("main")
            runCurrent()

            assertEquals("reply", contentType(1))
        }

    @Test
    fun publishedItemsSurviveAShorterPagingGeneration() =
        withPagingStates { pages, states ->
            pages.value = page("main", "reply")
            runCurrent()
            val published = states.last()

            pages.value = page("main")
            runCurrent()

            assertEquals("reply", published[1])
            assertEquals(published.peek(1), published[1])
        }

    @Test
    fun publishedKeysTypesAndItemsStayTogetherWhenItemsAreReordered() =
        withPagingStates { pages, states ->
            pages.value = page("main", "reply")
            runCurrent()
            val published = states.last()
            val itemKey = published.itemKey { it }
            val contentType = published.itemContentType { it }

            pages.value = page("reply", "main")
            runCurrent()

            assertEquals("main", itemKey(0))
            assertEquals("main", contentType(0))
            assertEquals("main", published[0])
            assertEquals("reply", states.last()[0])
        }

    @Test
    fun publishedItemsDoNotGrowWithANewerPagingGeneration() =
        withPagingStates { pages, states ->
            val published = states.last()

            pages.value = page("main", "reply")
            runCurrent()

            assertNull(published[-1])
            assertNull(published[1])
            assertEquals("reply", states.last()[1])
        }

    @Test
    fun currentItemsTriggerPrefetchWhilePublishedPlaceholdersStayStable() =
        withPagingStates { pages, states ->
            val loadedOffsets = mutableListOf<Int>()
            val pager =
                Pager(
                    config = PagingConfig(pageSize = 2, initialLoadSize = 2, prefetchDistance = 1, enablePlaceholders = true),
                    pagingSourceFactory = {
                        object : PagingSource<Int, String>() {
                            override fun getRefreshKey(state: androidx.paging.PagingState<Int, String>): Int? = null

                            override suspend fun load(params: LoadParams<Int>): LoadResult<Int, String> {
                                val start = params.key ?: 0
                                val end = minOf(start + params.loadSize, 6)
                                loadedOffsets += start
                                return LoadResult.Page(
                                    data = (start until end).map { "post-$it" },
                                    prevKey = null,
                                    nextKey = end.takeIf { it < 6 },
                                    itemsBefore = start,
                                    itemsAfter = 6 - end,
                                )
                            }
                        }
                    },
                )
            backgroundScope.launch {
                pager.flow.collect { pages.value = it }
            }
            runCurrent()
            val published = states.last()
            val itemKey = published.itemKey { it.removePrefix("post-").toInt() + 2 }
            val contentType = published.itemContentType { "post" }
            val placeholderKey = itemKey(2)
            val placeholderType = contentType(2)

            assertEquals(6, published.itemCount)
            assertNull(published.peek(2))
            assertEquals(published.itemKey()(2), placeholderKey)
            assertNotEquals(itemKey(0), placeholderKey)
            assertNotEquals(itemKey(3), placeholderKey)
            assertEquals(listOf(0), loadedOffsets)

            assertEquals("post-1", published[1])
            runCurrent()

            assertEquals(listOf(0, 2), loadedOffsets)
            assertEquals("post-2", states.last().peek(2))
            assertEquals(placeholderKey, itemKey(2))
            assertEquals(placeholderType, contentType(2))
            assertNull(published[5])
            runCurrent()
            assertEquals(listOf(0, 2), loadedOffsets)

            assertEquals("post-3", states.last()[3])
            runCurrent()
            assertEquals(listOf(0, 2, 4), loadedOffsets)
            assertEquals("post-5", states.last().peek(5))
        }

    @Test
    fun repliesPublishANewStateAfterTheMainPost() =
        withPagingStates { pages, states ->
            val mainOnly = states.last()

            pages.value = page("main", "reply")
            runCurrent()
            val withReplies = states.last()

            assertNotEquals(mainOnly, withReplies)
            assertEquals(1, mainOnly.itemCount)
            assertEquals(2, withReplies.itemCount)
            assertEquals("reply", withReplies.peek(1))
        }

    @Test
    fun editedPostsPublishANewStateWithoutChangingTheCount() =
        withPagingStates { pages, states ->
            val original = states.last()

            pages.value = page("edited main")
            runCurrent()
            val edited = states.last()

            assertNotEquals(original, edited)
            assertEquals("main", original.peek(0))
            assertEquals("edited main", edited.peek(0))
        }

    @Test
    fun refreshChangesPublishANewStateWithoutChangingTheItems() =
        withPagingStates { pages, states ->
            val idle = states.last()

            pages.value = page("main", refresh = LoadState.Loading)
            runCurrent()
            val refreshing = states.last()

            assertNotEquals(idle, refreshing)
            assertFalse(idle.isRefreshing)
            assertTrue(refreshing.isRefreshing)
        }

    private fun withPagingStates(
        block: suspend TestScope.(MutableStateFlow<PagingData<String>>, List<PagingState.Success<String>>) -> Unit,
    ) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val pages = MutableStateFlow(page("main"))
        val states = mutableListOf<PagingState.Success<String>>()
        val job =
            launch {
                moleculeFlow(RecompositionMode.Immediate) {
                    pages.collectAsLazyPagingItems().toPagingState()
                }.collect { state ->
                    state.onSuccess { states += this }
                }
            }
        try {
            runCurrent()
            block(pages, states)
        } finally {
            job.cancelAndJoin()
            Dispatchers.resetMain()
        }
    }

    private fun page(
        vararg items: String,
        refresh: LoadState = LoadState.NotLoading(endOfPaginationReached = false),
    ): PagingData<String> =
        PagingData.from(
            data = items.toList(),
            sourceLoadStates =
                LoadStates(
                    refresh = refresh,
                    prepend = LoadState.NotLoading(endOfPaginationReached = true),
                    append = LoadState.NotLoading(endOfPaginationReached = false),
                ),
        )
}
