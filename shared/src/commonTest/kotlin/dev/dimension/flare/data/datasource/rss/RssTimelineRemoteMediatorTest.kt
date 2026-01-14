package dev.dimension.flare.data.datasource.rss

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.dimension.flare.RobolectricTest
import dev.dimension.flare.common.BaseTimelineRemoteMediator
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.network.rss.model.Feed
import dev.dimension.flare.memoryDatabaseBuilder
import dev.dimension.flare.ui.model.mapper.parseRssDateToInstant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class RssTimelineRemoteMediatorTest : RobolectricTest() {
    private lateinit var cacheDb: CacheDatabase

    @BeforeTest
    fun setup() {
        cacheDb =
            Room
                .memoryDatabaseBuilder<CacheDatabase>()
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.Unconfined)
                .build()
    }

    @AfterTest
    fun tearDown() {
        cacheDb.close()
    }

    @Test
    fun sortIdFollowsPublicationDate() =
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
                    cacheDatabase = cacheDb,
                    fetchFeed = { feed },
                    fetchIcon = { _ -> null },
                    fetchSource = { _ -> null },
                )

            val result =
                mediator.timeline(
                    pageSize = 10,
                    request = BaseTimelineRemoteMediator.Request.Refresh,
                )

            val sortIds = result.data.map { it.timeline.sortId }.sortedDescending()
            val expectedSortIds =
                listOf(newestDate, middleDate, oldestDate).map {
                    requireNotNull(parseRssDateToInstant(it)).toEpochMilliseconds()
                }

            assertEquals(expectedSortIds, sortIds)
            assertEquals(
                expectedSortIds,
                sortIds,
                "sortId should follow publication date for correct timeline ordering",
            )
        }
}
