package dev.dimension.flare.data.datasource.microblog

import dev.dimension.flare.RobolectricTest
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.datasource.microblog.paging.historyTestPost
import dev.dimension.flare.data.model.tab.TimelineMergePolicy
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MixedTimelinePageLoaderTest : RobolectricTest() {
    @Test
    fun timeMergeRetainsPendingPostsAcrossReopeningAndOnlyFetchesOncePerSourcePerClick() =
        runTest {
            val first = Source("a", listOf(page(9, 6, next = "a2"), page(3)))
            val second = Source("b", listOf(page(8, 7, next = "b2"), page(5, 4, 2)))
            var result = MixedTimelinePageLoader(listOf(first, second), TimelineMergePolicy.Time).load(3, PagingRequest.Refresh)
            val ids = result.data.map { it.statusKey.id }.toMutableList()
            assertEquals(listOf("9", "8", "7"), ids)
            assertEquals(listOf<PagingRequest>(PagingRequest.Refresh), first.requests)
            assertEquals(listOf<PagingRequest>(PagingRequest.Refresh), second.requests)

            // Continuations do not rely on an in-memory mediator or a shared mutable DB cursor.
            val reopened = MixedTimelinePageLoader(listOf(first, second), TimelineMergePolicy.Time)
            repeat(5) {
                val key = result.nextKey ?: return@repeat
                result = reopened.load(3, PagingRequest.Append(key))
                ids += result.data.map { it.statusKey.id }
            }
            assertEquals(listOf("9", "8", "7", "6", "5", "4", "3", "2"), ids)
            assertNull(result.nextKey)
            assertEquals(2, first.requests.size)
            assertEquals(2, second.requests.size)
        }

    @Test
    fun failedSourceCanRetryWithoutRepeatingTheSuccessfulSourceOrDiscardingItsPosts() =
        runTest {
            for (policy in TimelineMergePolicy.entries) {
                val good = Source("good", listOf(page(4, 2)))
                var failed = true
                val bad =
                    object : CacheableRemoteLoader<UiTimelineV2> {
                        override val pagingKey = "bad"

                        override suspend fun load(
                            pageSize: Int,
                            request: PagingRequest,
                        ): PagingResult<UiTimelineV2> {
                            assertEquals(PagingRequest.Refresh, request)
                            if (failed) error("offline")
                            return page(3, 1)
                        }
                    }
                val mixed = MixedTimelinePageLoader(listOf(good, bad), policy)
                val first = mixed.load(20, PagingRequest.Refresh)
                assertEquals(listOf("4", "2"), first.data.map { it.statusKey.id })
                assertNotNull(first.continuationFailure)
                failed = false
                val retry = mixed.load(20, PagingRequest.Append(assertNotNull(first.nextKey)))
                assertEquals(listOf("3", "1"), retry.data.map { it.statusKey.id })
                assertNull(retry.continuationFailure)
                assertNull(retry.nextKey)
                assertEquals(1, good.requests.size)
            }
        }

    @Test
    fun anEmptyPageWithAContinuationIsNotExhaustion() =
        runTest {
            val sparse = Source("sparse", listOf(page(next = "more"), page(3)))
            val mixed = MixedTimelinePageLoader(listOf(sparse), TimelineMergePolicy.Time)
            val first = mixed.load(20, PagingRequest.Refresh)
            assertEquals(emptyList(), first.data)
            val next = mixed.load(20, PagingRequest.Append(assertNotNull(first.nextKey)))
            assertEquals(listOf("3"), next.data.map { it.statusKey.id })
            assertNull(next.nextKey)
        }

    @Test
    fun sourceOrderAndMergePolicyArePartOfTheHistoryIdentity() {
        val a = Source("a", emptyList())
        val b = Source("b", emptyList())
        val keys =
            TimelineMergePolicy.entries.flatMap { policy ->
                listOf(listOf(a, b), listOf(b, a)).map { MixedTimelinePageLoader(it, policy).pagingKey }
            }
        assertEquals(keys.size, keys.toSet().size)
    }

    private class Source(
        override val pagingKey: String,
        private val responses: List<PagingResult<UiTimelineV2>>,
    ) : CacheableRemoteLoader<UiTimelineV2> {
        val requests = mutableListOf<PagingRequest>()

        override suspend fun load(
            pageSize: Int,
            request: PagingRequest,
        ): PagingResult<UiTimelineV2> {
            requests += request
            return responses[requests.lastIndex]
        }
    }

    private fun page(
        vararg ids: Int,
        next: String? = null,
    ): PagingResult<UiTimelineV2> = PagingResult(ids.map(::historyTestPost), nextKey = next)
}
