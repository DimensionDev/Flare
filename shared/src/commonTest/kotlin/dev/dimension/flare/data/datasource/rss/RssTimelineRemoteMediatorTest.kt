package dev.dimension.flare.data.datasource.rss

import dev.dimension.flare.RobolectricTest
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.network.rss.model.Feed
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.mapper.parseRssDateToInstant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalCoroutinesApi::class)
class RssTimelineRemoteMediatorTest : RobolectricTest() {
    @Test
    fun feedItemsUsePublicationDateAsCreatedAt() =
        runTest {
            val middleDate = "Wed, 03 Jan 2024 10:00:00 +0000"
            val newestDate = "Fri, 05 Jan 2024 10:00:00 +0000"
            val oldestDate = "Mon, 01 Jan 2024 10:00:00 +0000"

            val feed =
                Feed.Rss20(
                    version = "2.0",
                    channel =
                        Feed.Rss20.Channel(
                            title = "Test feed",
                            link = "https://example.com",
                            description = "desc",
                            items =
                                listOf(
                                    Feed.Rss20.Item(
                                        title = "Middle",
                                        link = "https://example.com/middle",
                                        description = "middle",
                                        pubDate = middleDate,
                                    ),
                                    Feed.Rss20.Item(
                                        title = "Newest",
                                        link = "https://example.com/new",
                                        description = "new",
                                        pubDate = newestDate,
                                    ),
                                    Feed.Rss20.Item(
                                        title = "Oldest",
                                        link = "https://example.com/old",
                                        description = "old",
                                        pubDate = oldestDate,
                                    ),
                                ),
                        ),
                )

            val mediator =
                RssTimelineRemoteMediator(
                    url = "https://example.com/rss",
                    fetchFeed = { feed },
                    fetchIcon = { _ -> null },
                    fetchSource = { _ -> null },
                )

            val result =
                mediator.load(
                    pageSize = 10,
                    request = PagingRequest.Refresh,
                )

            val byTitle = result.data.filterIsInstance<UiTimelineV2.Feed>().associateBy { it.title }
            assertEquals(3, byTitle.size)

            assertEquals(
                requireNotNull(parseRssDateToInstant(middleDate)).toEpochMilliseconds(),
                byTitle["Middle"]?.createdAt?.value?.toEpochMilliseconds(),
            )
            assertEquals(
                requireNotNull(parseRssDateToInstant(newestDate)).toEpochMilliseconds(),
                byTitle["Newest"]?.createdAt?.value?.toEpochMilliseconds(),
            )
            assertEquals(
                requireNotNull(parseRssDateToInstant(oldestDate)).toEpochMilliseconds(),
                byTitle["Oldest"]?.createdAt?.value?.toEpochMilliseconds(),
            )
            assertNotNull(byTitle["Middle"])
            assertNotNull(byTitle["Newest"])
            assertNotNull(byTitle["Oldest"])
        }
}
