package dev.dimension.flare.data.datasource.mastodon

import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.network.mastodon.api.TrendsResources
import dev.dimension.flare.data.network.mastodon.api.model.Account
import dev.dimension.flare.data.network.mastodon.api.model.Status
import dev.dimension.flare.data.network.mastodon.api.model.Suggestions
import dev.dimension.flare.data.network.mastodon.api.model.Trend
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DiscoverStatusRemoteMediatorTest {
    @Test
    fun loadsFollowingPagesUsingOffset() =
        runTest {
            val service = FakeTrendsResources()
            val mediator =
                DiscoverStatusRemoteMediator(
                    service = service,
                    accountKey = MicroBlogKey(id = "me", host = "mastodon.social"),
                )

            val firstPage = mediator.load(pageSize = 20, request = PagingRequest.Refresh)
            val secondPage =
                mediator.load(
                    pageSize = 20,
                    request = PagingRequest.Append(checkNotNull(firstPage.nextKey)),
                )
            val lastPage =
                mediator.load(
                    pageSize = 20,
                    request = PagingRequest.Append(checkNotNull(secondPage.nextKey)),
                )

            assertEquals(
                listOf<Pair<Int?, Int?>>(20 to 0, 20 to 20, 20 to 40),
                service.requests,
            )
            assertEquals(20, firstPage.data.size)
            assertEquals("20", firstPage.nextKey)
            assertEquals(20, secondPage.data.size)
            assertEquals("40", secondPage.nextKey)
            assertEquals(3, lastPage.data.size)
            assertNull(lastPage.nextKey)
        }

    private class FakeTrendsResources : TrendsResources {
        val requests = mutableListOf<Pair<Int?, Int?>>()

        override suspend fun trendsTags(): List<Trend> = emptyList()

        override suspend fun trendsStatuses(
            limit: Int?,
            offset: Int?,
        ): List<Status> {
            requests += limit to offset
            val count = if (offset == 40) 3 else 20
            return List(count) { index ->
                val id = (offset.orZero() + index).toString()
                Status(
                    id = id,
                    account = Account(id = "account-$id"),
                )
            }
        }

        override suspend fun suggestionsUsers(limit: Int?): List<Suggestions> = emptyList()

        private fun Int?.orZero(): Int = this ?: 0
    }
}
