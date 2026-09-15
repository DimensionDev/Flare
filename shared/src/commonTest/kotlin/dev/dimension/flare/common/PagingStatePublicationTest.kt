package dev.dimension.flare.common

import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PagingStatePublicationTest {
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
