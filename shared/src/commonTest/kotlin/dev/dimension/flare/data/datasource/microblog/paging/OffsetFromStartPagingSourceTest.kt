package dev.dimension.flare.data.datasource.microblog.paging

import androidx.paging.PagingSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class OffsetFromStartPagingSourceTest {
    @Test
    fun loadWaitsForInvalidationInitialEmissionBeforeReading() =
        runTest {
            val invalidationJob = Job()
            val ready = CompletableDeferred<Unit>()
            var loadCalls = 0
            val pagingSource =
                OffsetFromStartPagingSource(
                    object : OffsetFromStartPageLoader<Int> {
                        override suspend fun load(
                            offset: Int,
                            limit: Int,
                        ): List<Int> {
                            assertEquals(0, offset)
                            return listOf(++loadCalls)
                        }

                        override fun observeInvalidations(invalidate: () -> Unit): PageInvalidationSubscription =
                            PageInvalidationSubscription(
                                job = invalidationJob,
                                ready = ready,
                            )
                    },
                )

            val resultDeferred =
                async {
                    pagingSource.doLoad(
                        PagingSource.LoadParams.Refresh(
                            key = null,
                            loadSize = 1,
                            placeholdersEnabled = false,
                        ),
                    )
                }

            yield()
            assertEquals(0, loadCalls)
            ready.complete(Unit)
            val result = withTimeout(1_000) { resultDeferred.await() }

            val page = assertIs<PagingSource.LoadResult.Page<OffsetFromStartPagingKey, Int>>(result)
            assertEquals(listOf(1), page.data)
            invalidationJob.cancel()
        }

    @Test
    fun refreshKeyReloadsFromStartThroughAnchorWindow() {
        val pagingSource =
            OffsetFromStartPagingSource(
                object : OffsetFromStartPageLoader<Int> {
                    override suspend fun load(
                        offset: Int,
                        limit: Int,
                    ): List<Int> = emptyList()
                },
            )
        val refreshKey =
            pagingSource.getRefreshKey(
                PagingSource.LoadResult
                    .Page<OffsetFromStartPagingKey, Int>(
                        data = (0 until 40).toList(),
                        prevKey = null,
                        nextKey = OffsetFromStartPagingKey.Append(40),
                    ).let { page ->
                        androidx.paging.PagingState(
                            pages = listOf(page),
                            anchorPosition = 25,
                            config =
                                androidx.paging.PagingConfig(
                                    pageSize = 20,
                                    prefetchDistance = 1,
                                    initialLoadSize = 20,
                                ),
                            leadingPlaceholderCount = 0,
                        )
                    },
            )

        assertEquals(OffsetFromStartPagingKey.Refresh(limit = 47), refreshKey)
    }
}
